package com.example.trackingms.interfaces.rest;

import com.example.trackingms.domain.commands.InitializeTrackingCommand;
import com.example.trackingms.domain.model.JwtToken;
import com.example.trackingms.domain.model.TokenRole;
import com.example.trackingms.domain.model.TrackingNumber;
import com.example.trackingms.domain.projections.TrackingSummary;
import com.example.trackingms.domain.services.TrackingTokenService;
import com.example.trackingms.interfaces.rest.dto.PublicTrackingResponse;
import org.axonframework.commandhandling.gateway.CommandGateway;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * 公開追跡照会エンドポイントの End-to-End 統合テスト（US18 / ADR-0013 / IT6 タスク 1.3）。
 *
 * <p>local-h2 で Spring Boot を起動し、{@link PublicTrackingTokenFilter} → JWT 検証 →
 * {@link PublicTrackingController} → {@code tracking_summary} / {@code tracking_event} までを
 * 物理経路で貫通検証する。Kafka は使わない。</p>
 *
 * <p>IT5 H6 対応の {@code @DirtiesContext(BEFORE_EACH_TEST_METHOD)} を踏襲し、テスト間の
 * event store 汚染を防ぐ。</p>
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "axon.axonserver.enabled=false",
                "axon.kafka.publisher.enabled=false",
                "axon.kafka.fetcher.enabled=false"
        })
@ActiveProfiles("local-h2")
@org.springframework.test.annotation.DirtiesContext(
        classMode = org.springframework.test.annotation.DirtiesContext.ClassMode.BEFORE_EACH_TEST_METHOD)
class PublicTrackingControllerIntegrationTest {

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private CommandGateway commandGateway;

    @Autowired
    private TrackingTokenService tokenService;

    @Test
    @DisplayName("US18: 有効な JWT で公開照会するとサマリと履歴が返る")
    void 有効JWTで照会できる() {
        String tn = "TRK-PUB0000001";
        commandGateway.sendAndWait(new InitializeTrackingCommand(tn, "B-PUB-001"));
        awaitProjection(tn);

        JwtToken token = tokenService.issue(TrackingNumber.of(tn),
                "shipper-001", TokenRole.SHIPPER, null);

        ResponseEntity<PublicTrackingResponse> response = restTemplate.getForEntity(
                "/api/v1/public/tracking/" + tn + "?token=" + token.token(),
                PublicTrackingResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().summary().trackingNumber()).isEqualTo(tn);
        assertThat(response.getBody().summary().currentStatus()).isEqualTo("NOT_RECEIVED");
        assertThat(response.getBody().events()).isNotEmpty();
    }

    @Test
    @DisplayName("ADR-0013: トークンなしで公開照会すると 403 Forbidden")
    void トークンなしは403() {
        String tn = "TRK-PUB0000002";
        commandGateway.sendAndWait(new InitializeTrackingCommand(tn, "B-PUB-002"));
        awaitProjection(tn);

        ResponseEntity<String> response = restTemplate.getForEntity(
                "/api/v1/public/tracking/" + tn,
                String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    @DisplayName("ADR-0013: 不正なトークン（別鍵署名相当）で公開照会すると 403 Forbidden")
    void 不正トークンは403() {
        String tn = "TRK-PUB0000003";
        commandGateway.sendAndWait(new InitializeTrackingCommand(tn, "B-PUB-003"));
        awaitProjection(tn);

        ResponseEntity<String> response = restTemplate.getForEntity(
                "/api/v1/public/tracking/" + tn + "?token=not-a-valid-jwt",
                String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    @DisplayName("ADR-0013: 別の追跡番号で発行したトークンは tn 不一致で 403 Forbidden")
    void トークンの追跡番号取り違えは403() {
        String tnA = "TRK-PUB0000004";
        String tnB = "TRK-PUB0000005";
        commandGateway.sendAndWait(new InitializeTrackingCommand(tnA, "B-PUB-004"));
        commandGateway.sendAndWait(new InitializeTrackingCommand(tnB, "B-PUB-005"));
        awaitProjection(tnA);
        awaitProjection(tnB);

        JwtToken tokenForA = tokenService.issue(TrackingNumber.of(tnA),
                "shipper-001", TokenRole.SHIPPER, null);

        // tnA で発行したトークンで tnB を照会
        ResponseEntity<String> response = restTemplate.getForEntity(
                "/api/v1/public/tracking/" + tnB + "?token=" + tokenForA.token(),
                String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    @DisplayName("US18: 存在しない追跡番号は filter は通過するが Controller で 404")
    void 存在しない追跡番号は404() {
        String tn = "TRK-NOPE000000";
        // 存在しないが、フォーマットは正しいので filter を通る。
        JwtToken token = tokenService.issue(TrackingNumber.of(tn),
                "shipper-001", TokenRole.SHIPPER, null);

        ResponseEntity<String> response = restTemplate.getForEntity(
                "/api/v1/public/tracking/" + tn + "?token=" + token.token(),
                String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    private void awaitProjection(String trackingNumber) {
        await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> {
            TrackingSummary s = restTemplate.getForObject(
                    "/api/v1/tracking/" + trackingNumber, TrackingSummary.class);
            assertThat(s).isNotNull();
        });
    }
}
