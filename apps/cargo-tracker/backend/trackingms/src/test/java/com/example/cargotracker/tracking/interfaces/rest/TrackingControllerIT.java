package com.example.cargotracker.tracking.interfaces.rest;

import static org.assertj.core.api.Assertions.assertThat;

import static org.awaitility.Awaitility.await;

import com.example.cargotracker.shared.contract.command.InitializeTrackingCommand;
import com.example.cargotracker.shared.contract.event.TrackingInitializedEvent;
import com.example.cargotracker.shared.testing.AbstractAxonIntegrationTest;
import com.example.cargotracker.tracking.infrastructure.projection.TrackingProjection;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.web.client.RestClient;

/**
 * 追跡一覧・詳細と手動更新の HTTP（S40・S41 / US17・US18）。
 *
 * <p><b>荷主 ID はヘッダから読む。</b> Gateway が JWT から取り出して伝える。
 * クライアントの指定を信じると、他社の追跡まで見えてしまう。</p>
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class TrackingControllerIT extends AbstractAxonIntegrationTest {

    static class JsonMap extends LinkedHashMap<String, Object> {
        private static final long serialVersionUID = 1L;
    }

    @LocalServerPort
    private int port;

    @Autowired
    private TrackingProjection projection;

    @Autowired
    private org.axonframework.messaging.commandhandling.gateway.CommandGateway commands;

    private final RestClient rest = RestClient.builder()
            .defaultStatusHandler(status -> true, (request, response) -> { })
            .build();

    private String given(String shipperId) {
        String trackingNumber = "TRK-T" + System.nanoTime() % 1000000000L;
        projection.on(new TrackingInitializedEvent(trackingNumber, "b-" + System.nanoTime(),
                shipperId, "JPTYO", "USNYC", "GENERAL",
                List.of(new TrackingInitializedEvent.Leg("V-MOL-001", "JPTYO", "USNYC",
                        Instant.parse("2026-09-10T09:00:00Z"),
                        Instant.parse("2026-09-24T18:00:00Z"))),
                Instant.parse("2026-09-08T01:00:00Z")));
        return trackingNumber;
    }

    /**
     * 追跡を<b>コマンドから</b>作る。
     *
     * <p>状態を動かす検査では投影に行を書くだけでは足りない。集約が無ければ
     * 「始まっていません」で断られる——<b>読み口が緑でも、動かす経路は通らない</b>。</p>
     */
    private String givenAggregate(String shipperId) {
        String trackingNumber = "TRK-A" + System.nanoTime() % 1000000000L;
        commands.sendAndWait(new InitializeTrackingCommand(trackingNumber,
                "b-" + System.nanoTime(), shipperId, "JPTYO", "USNYC", "GENERAL",
                List.of(new InitializeTrackingCommand.LegDto("V-MOL-001", "JPTYO", "USNYC",
                        Instant.parse("2026-09-10T09:00:00Z"),
                        Instant.parse("2026-09-24T18:00:00Z"))),
                Instant.parse("2026-09-08T01:00:00Z")), String.class);
        await().atMost(Duration.ofSeconds(30)).untilAsserted(() ->
                assertThat(get("/api/v1/tracking/trackings/" + trackingNumber, null)
                        .getStatusCode()).isEqualTo(HttpStatus.OK));
        return trackingNumber;
    }

    private ResponseEntity<JsonMap> get(String path, String shipperId) {
        var request = rest.get().uri("http://localhost:" + port + path);
        if (shipperId != null) {
            request = request.header("X-Auth-Shipper-Id", shipperId);
        }
        return request.retrieve().toEntity(JsonMap.class);
    }

    @Test
    @DisplayName("US17 §1: 追跡管理者は追跡詳細を読める（次に動かせる先も来る）")
    void servesDetailToTrackers() {
        String trackingNumber = given("SHP-000001");

        var response = get("/api/v1/tracking/trackings/" + trackingNumber, null);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).containsEntry("statusLabel", "未受領");
        // **画面が遷移表を持たない。** 持つと、集約が断る先を画面が出してしまう。
        assertThat(response.getBody().get("nextStatuses"))
                .asInstanceOf(org.assertj.core.api.InstanceOfAssertFactories.list(String.class))
                .containsExactlyInAnyOrder("RECEIVED", "MISROUTED");
    }

    @Test
    @DisplayName("US18: 荷主には自社のぶんだけ（他社のものは見つからない）")
    void hidesOtherShippersTrackings() {
        String mine = given("SHP-000001");
        String other = given("SHP-000999");

        assertThat(get("/api/v1/tracking/trackings/" + mine, "SHP-000001").getStatusCode())
                .isEqualTo(HttpStatus.OK);
        // **権限が無いことを伝えない。** 伝えると、その番号が実在すると分かる。
        assertThat(get("/api/v1/tracking/trackings/" + other, "SHP-000001").getStatusCode())
                .isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    @DisplayName("US18: 一覧も荷主で絞る")
    void filtersTheListByShipper() {
        String mine = given("SHP-000002");
        String other = given("SHP-000998");

        var response = get("/api/v1/tracking/trackings?limit=200", "SHP-000002");

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> items = (List<Map<String, Object>>) response.getBody()
                .get("items");
        assertThat(items).extracting(item -> item.get("trackingNumber"))
                .contains(mine)
                .doesNotContain(other);
    }

    @Test
    @DisplayName("US17 §2: 状態を手で更新でき、履歴と現在値に載る")
    void updatesStatusManually() {
        String trackingNumber = givenAggregate("SHP-000001");

        var updated = rest.post()
                .uri("http://localhost:" + port + "/api/v1/tracking/trackings/"
                        + trackingNumber + "/status")
                .header("X-Auth-Username", "tracker01")
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("newStatus", "RECEIVED", "location", "JPTYO"))
                .retrieve().toBodilessEntity();

        assertThat(updated.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);

        // 投影は非同期に追いつく。
        await().atMost(Duration.ofSeconds(30)).untilAsserted(() ->
                assertThat(get("/api/v1/tracking/trackings/" + trackingNumber, null).getBody())
                        .containsEntry("statusLabel", "受領済"));
        var detail = get("/api/v1/tracking/trackings/" + trackingNumber, null);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> history = (List<Map<String, Object>>) detail.getBody()
                .get("history");
        assertThat(history).hasSize(1);
        // **更新者はヘッダから取る。** 本文に載せると、他人の名前で記録できる。
        assertThat(history.get(0)).containsEntry("recordedBy", "tracker01");
        assertThat(history.get(0)).containsEntry("eventType", "MANUAL");
    }

    @Test
    @DisplayName("正典が許さない遷移は断る（500 にしない）")
    void rejectsForbiddenTransitions() {
        String trackingNumber = givenAggregate("SHP-000001");

        var response = rest.post()
                .uri("http://localhost:" + port + "/api/v1/tracking/trackings/"
                        + trackingNumber + "/status")
                .header("X-Auth-Username", "tracker01")
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("newStatus", "DELIVERED"))
                .retrieve().toEntity(JsonMap.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody().get("message").toString())
                .as("断った理由が読めないと、追跡管理者は次に何をすればよいか分からない")
                .contains("未受領")
                .contains("引取済");
    }

    @Test
    @DisplayName("知らない状態の名前は 422（壊れたのではなく入力の誤り）")
    void rejectsUnknownStatusNames() {
        String trackingNumber = givenAggregate("SHP-000001");

        var response = rest.post()
                .uri("http://localhost:" + port + "/api/v1/tracking/trackings/"
                        + trackingNumber + "/status")
                .header("X-Auth-Username", "tracker01")
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("newStatus", "FLYING"))
                .retrieve().toBodilessEntity();

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.valueOf(422));
    }
}
