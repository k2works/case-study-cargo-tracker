package com.example.cargotracker.tracking.interfaces.rest;

import java.math.BigDecimal;
import static org.assertj.core.api.Assertions.assertThat;

import com.example.cargotracker.shared.contract.event.TrackingInitializedEvent;
import com.example.cargotracker.shared.testing.AbstractAxonIntegrationTest;
import com.example.cargotracker.tracking.infrastructure.projection.TrackingProjection;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.web.client.RestClient;

/**
 * 公開追跡照会の HTTP（S44 / US18）。<b>認証ヘッダを送らない</b>。
 *
 * <p>荷受人はロールを持たない。<b>ここが 401 になると社外からは入れない</b>ので、
 * 「認証ヘッダ無しで 200 が返る」ことを画面ではなく HTTP で固定する。</p>
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class PublicTrackingControllerIT extends AbstractAxonIntegrationTest {

    static class JsonMap extends LinkedHashMap<String, Object> {
        private static final long serialVersionUID = 1L;
    }

    @LocalServerPort
    private int port;

    @Autowired
    private TrackingProjection projection;

    private final RestClient rest = RestClient.builder()
            .defaultStatusHandler(status -> true, (request, response) -> { })
            .build();

    private ResponseEntity<JsonMap> get(String trackingNumber) {
        return rest.get()
                .uri("http://localhost:" + port + "/api/v1/tracking/public/" + trackingNumber)
                .retrieve()
                .toEntity(JsonMap.class);
    }

    @Test
    @DisplayName("US18 §5: ログインなしで照会できる")
    void servesWithoutAuthentication() {
        String trackingNumber = "TRK-C" + System.nanoTime() % 1000000000L;
        projection.on(new TrackingInitializedEvent(trackingNumber, "b-" + System.nanoTime(),
                "SHP-000001", "JPTYO", "USNYC", "GENERAL",
                new BigDecimal("1200"),
                List.of(new TrackingInitializedEvent.Leg("V-MOL-001", "JPTYO", "USNYC",
                        Instant.parse("2026-09-10T09:00:00Z"),
                        Instant.parse("2026-09-24T18:00:00Z"))),
                Instant.parse("2026-09-08T01:00:00Z")));

        var response = get(trackingNumber);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).containsEntry("statusLabel", "未受領");
        // **公開応答に荷主 ID を入れない。** 画面が間違えても漏れないようにする。
        assertThat(response.getBody()).doesNotContainKey("shipperId");
        assertThat(response.getBody()).doesNotContainKey("bookingId");
    }

    @Test
    @DisplayName("見つからないときは 404（存在しない番号と権限の無い番号を区別しない）")
    void returnsNotFoundForUnknownNumbers() {
        assertThat(get("TRK-NOSUCHNUM").getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }
}
