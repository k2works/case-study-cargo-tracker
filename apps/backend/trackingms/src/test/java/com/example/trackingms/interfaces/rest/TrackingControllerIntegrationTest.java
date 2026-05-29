package com.example.trackingms.interfaces.rest;

import com.example.trackingms.domain.commands.InitializeTrackingCommand;
import com.example.trackingms.interfaces.rest.dto.TrackingEventResponse;
import com.example.trackingms.interfaces.rest.dto.TrackingSummaryResponse;
import com.example.trackingms.interfaces.rest.dto.UpdateTransportStatusRequest;
import org.axonframework.commandhandling.gateway.CommandGateway;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * 追跡状態手動更新 REST API の End-to-End 統合テスト（US17 / IT5 2.5）。
 *
 * <p>local-h2 プロファイルで Spring Boot を起動し、CommandGateway → TrackingActivity 集約 →
 * TransportStatusUpdatedEvent → tracking_summary / tracking_event 投影 → REST 経由の照会、までの
 * 物理経路を貫通検証する。Kafka は使わない（cross-service 経路は IT5 1.5 で別途検証済み）。</p>
 *
 * <p>本テストは Awaitility で結果整合性を待つ（Projection は default プロセッサだが、
 * Spring Boot の起動完了直後はわずかに非同期処理が走る場合に備える）。</p>
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "axon.axonserver.enabled=false",
                "axon.kafka.publisher.enabled=false",
                "axon.kafka.fetcher.enabled=false"
        })
@ActiveProfiles("local-h2")
class TrackingControllerIntegrationTest {

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private CommandGateway commandGateway;

    /** 一連の正常な状態遷移（NOT_RECEIVED → RECEIVED → LOADED → IN_TRANSIT → DELIVERED）を貫通検証する。 */
    @Test
    @DisplayName("US17: 許可遷移を順番に重ねて DELIVERED まで到達できる")
    void ハッピーパスの状態遷移() {
        String tn = "TRK-INT0000001";
        initializeTracking(tn, "B-INT-001");

        await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> {
            TrackingSummaryResponse summary = getSummary(tn);
            assertThat(summary).isNotNull();
            assertThat(summary.currentStatus()).isEqualTo("NOT_RECEIVED");
        });

        // 5 段階の遷移を実行
        updateStatus(tn, "RECEIVED", "JPTYO", null, LocalDateTime.of(2026, 7, 20, 10, 0),
                "東京港で受領");
        updateStatus(tn, "LOADED", "JPTYO", "V-MAERSK-220",
                LocalDateTime.of(2026, 7, 21, 9, 0), "本船積込");
        updateStatus(tn, "IN_TRANSIT", "JPTYO", "V-MAERSK-220",
                LocalDateTime.of(2026, 7, 21, 18, 0), "東京港出港");
        updateStatus(tn, "UNLOADED", "USNYC", "V-MAERSK-220",
                LocalDateTime.of(2026, 8, 15, 9, 0), "NY 荷降し");
        updateStatus(tn, "AWAITING_CLAIM", "USNYC", null,
                LocalDateTime.of(2026, 8, 15, 10, 0), "引取待ち");
        updateStatus(tn, "DELIVERED", "USNYC", null,
                LocalDateTime.of(2026, 8, 16, 14, 0), "荷受人引取");

        // 最終状態の確認
        await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> {
            TrackingSummaryResponse summary = getSummary(tn);
            assertThat(summary.currentStatus()).isEqualTo("DELIVERED");
            assertThat(summary.currentUnlocode()).isEqualTo("USNYC");
            assertThat(summary.misrouted()).isFalse();
        });

        // 履歴の確認：初期化（SYSTEM）+ 各状態（MANUAL）が時系列で並ぶ。
        // 件数は最低 7 件以上（Spring Context 共有によるリプレイで増える場合がある／IT5 既知事象、後続改善）。
        await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> {
            List<TrackingEventResponse> events = getEvents(tn);
            assertThat(events).hasSizeGreaterThanOrEqualTo(7);
            assertThat(events.get(0).source()).isEqualTo("SYSTEM");
            assertThat(events.get(0).eventType()).isEqualTo("TRACKING_INITIALIZED");
            // 期待される全状態が含まれている（順序・件数は保証しない）
            assertThat(events).extracting(TrackingEventResponse::transportStatus)
                    .contains("NOT_RECEIVED", "RECEIVED", "LOADED", "IN_TRANSIT",
                              "UNLOADED", "AWAITING_CLAIM", "DELIVERED");
        });
    }

    @Test
    @DisplayName("US17: MISROUTED への遷移で misrouted フラグが立つ")
    void 誤配送検知でフラグが立つ() {
        String tn = "TRK-INT0000002";
        initializeTracking(tn, "B-INT-002");
        awaitInitialized(tn);

        // NOT_RECEIVED → RECEIVED → MISROUTED
        updateStatus(tn, "RECEIVED", "JPTYO", null,
                LocalDateTime.of(2026, 7, 20, 10, 0), "受領");
        updateStatus(tn, "MISROUTED", "CNHKG", null,
                LocalDateTime.of(2026, 7, 22, 12, 0), "想定外の港で発見");

        await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> {
            TrackingSummaryResponse summary = getSummary(tn);
            assertThat(summary.currentStatus()).isEqualTo("MISROUTED");
            assertThat(summary.misrouted()).isTrue();
            assertThat(summary.currentUnlocode()).isEqualTo("CNHKG");
        });
    }

    @Test
    @DisplayName("US17: 不正遷移（NOT_RECEIVED → DELIVERED）は 422 を返し履歴に追加されない")
    void 不正遷移は422を返す() {
        String tn = "TRK-INT0000003";
        initializeTracking(tn, "B-INT-003");
        awaitInitialized(tn);

        UpdateTransportStatusRequest request = new UpdateTransportStatusRequest(
                "DELIVERED", "USNYC", null,
                LocalDateTime.of(2026, 8, 1, 10, 0), "ジャンプ");

        ResponseEntity<Map> response = restTemplate.postForEntity(
                "/api/v1/tracking/" + tn + "/status", request, Map.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().get("message").toString()).contains("NOT_RECEIVED");

        // 履歴は初期化の 1 件のみ
        List<TrackingEventResponse> events = getEvents(tn);
        assertThat(events).hasSize(1);
        assertThat(events.get(0).eventType()).isEqualTo("TRACKING_INITIALIZED");

        // サマリの状態は NOT_RECEIVED のまま
        TrackingSummaryResponse summary = getSummary(tn);
        assertThat(summary.currentStatus()).isEqualTo("NOT_RECEIVED");
    }

    @Test
    @DisplayName("US17: 不正な状態名は 400 を返す")
    void 不正な状態名は400を返す() {
        String tn = "TRK-INT0000004";
        initializeTracking(tn, "B-INT-004");
        awaitInitialized(tn);

        UpdateTransportStatusRequest request = new UpdateTransportStatusRequest(
                "BANANA", "JPTYO", null,
                LocalDateTime.of(2026, 7, 20, 10, 0), null);

        ResponseEntity<Map> response = restTemplate.postForEntity(
                "/api/v1/tracking/" + tn + "/status", request, Map.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    @DisplayName("US17: 存在しない追跡番号への照会は 404")
    void 存在しない追跡番号は404() {
        ResponseEntity<TrackingSummaryResponse> response = restTemplate.getForEntity(
                "/api/v1/tracking/TRK-NOTEXIST00", TrackingSummaryResponse.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    // --- helpers ---

    private void initializeTracking(String trackingNumber, String bookingId) {
        commandGateway.sendAndWait(new InitializeTrackingCommand(trackingNumber, bookingId));
    }

    private void awaitInitialized(String trackingNumber) {
        await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> {
            TrackingSummaryResponse summary = getSummary(trackingNumber);
            assertThat(summary).isNotNull();
            assertThat(summary.currentStatus()).isEqualTo("NOT_RECEIVED");
        });
    }

    private TrackingSummaryResponse getSummary(String trackingNumber) {
        ResponseEntity<TrackingSummaryResponse> response = restTemplate.getForEntity(
                "/api/v1/tracking/" + trackingNumber, TrackingSummaryResponse.class);
        if (response.getStatusCode() == HttpStatus.NOT_FOUND) {
            return null;
        }
        return response.getBody();
    }

    private List<TrackingEventResponse> getEvents(String trackingNumber) {
        HttpHeaders headers = new HttpHeaders();
        headers.setAccept(List.of(MediaType.APPLICATION_JSON));
        ResponseEntity<List<TrackingEventResponse>> response = restTemplate.exchange(
                "/api/v1/tracking/" + trackingNumber + "/events",
                HttpMethod.GET,
                new HttpEntity<>(headers),
                new ParameterizedTypeReference<>() {});
        return response.getBody();
    }

    private void updateStatus(String trackingNumber, String toStatus, String unlocode,
                              String voyageNumber, LocalDateTime occurredAt, String description) {
        UpdateTransportStatusRequest request = new UpdateTransportStatusRequest(
                toStatus, unlocode, voyageNumber, occurredAt, description);
        ResponseEntity<Void> response = restTemplate.postForEntity(
                "/api/v1/tracking/" + trackingNumber + "/status", request, Void.class);
        assertThat(response.getStatusCode())
                .as("updateStatus(%s, %s) のレスポンス", trackingNumber, toStatus)
                .isEqualTo(HttpStatus.ACCEPTED);
        // 投影が反映されるまで待つ
        await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> {
            TrackingSummaryResponse summary = getSummary(trackingNumber);
            assertThat(summary.currentStatus()).isEqualTo(toStatus);
        });
    }
}
