package com.example.cargotracker.handling.interfaces.rest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import com.example.cargotracker.handling.infrastructure.projection.CargoSnapshotProjection;
import com.example.cargotracker.shared.contract.event.TrackingInitializedEvent;
import com.example.cargotracker.shared.testing.AbstractAxonIntegrationTest;
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
 * 引取の通関ガード（US29 §受入基準 3 / IT12 T7）。
 *
 * <p><b>荷役の記録の検査から分けている。</b> ガードが見ているのは「通関が済んで
 * いるか」で、荷役の記録が見ているのは「作業を残せるか」である。まとめて置くと、
 * 片方を読むためにもう片方を読み飛ばすことになる。</p>
 *
 * <p><b>層が全部緑でも配線は未検査。</b> 通関状態はコマンドに載せて運ぶので、
 * 解決する層が読み違えていても集約の検査は緑のままになる。</p>
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class ClaimCustomsGuardIT extends AbstractAxonIntegrationTest {

    static class JsonMap extends LinkedHashMap<String, Object> {
        private static final long serialVersionUID = 1L;
    }

    @LocalServerPort
    private int port;

    @Autowired
    private CargoSnapshotProjection cargos;

    private final RestClient rest = RestClient.builder()
            .defaultStatusHandler(status -> true, (request, response) -> { })
            .build();

    private String url(String path) {
        return "http://localhost:" + port + "/api/v1/handling" + path;
    }

    private String givenCargo() {
        String trackingNumber = "TRK-G" + System.nanoTime() % 1000000000L;
        cargos.on(new TrackingInitializedEvent(trackingNumber, "b-" + System.nanoTime(),
                "SHP-000001", "JPTYO", "USNYC", "GENERAL",
                List.of(new TrackingInitializedEvent.Leg("V-MOL-001", "JPTYO", "USNYC",
                        Instant.parse("2026-09-10T09:00:00Z"),
                        Instant.parse("2026-09-16T08:00:00Z"))),
                Instant.parse("2026-09-08T01:00:00Z")), "evt-" + System.nanoTime());
        return trackingNumber;
    }

    private Map<String, Object> request(String trackingNumber, String type, String unLocode) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("activityId", "act-" + System.nanoTime());
        body.put("trackingNumber", trackingNumber);
        body.put("handlingType", type);
        body.put("unLocode", unLocode);
        if (!"CLAIM".equals(type)) {
            body.put("voyageNumber", "V-MOL-001");
        }
        return body;
    }

    private ResponseEntity<JsonMap> register(Map<String, Object> body) {
        return rest.post().uri(url("/activities"))
                .header("X-Auth-Username", "handler01")
                .contentType(MediaType.APPLICATION_JSON)
                .body(body).retrieve().toEntity(JsonMap.class);
    }

    @Test
    @DisplayName("US29 §3: 通関申告が無い貨物の引取は断られ、現在の通関状態が出る")
    void refusesClaimWithoutCustomsClearance() {
        // **層が全部緑でも配線は未検査。** 通関状態はコマンドに載せて運ぶので、
        // 解決する層が読み違えていても集約の検査は緑のままになる。
        String trackingNumber = givenCargo();
        register(request(trackingNumber, "UNLOAD", "USNYC"));
        var body = request(trackingNumber, "CLAIM", "USNYC");
        body.put("consigneeName", "John Smith");

        var response = register(body);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(String.valueOf(response.getBody())).contains("通関申告がありません");
    }

    @Test
    @DisplayName("US29 §3: 審査中のままでは引取できず、判定に使った状態が返る")
    void refusesClaimWhileCustomsPending() {
        String trackingNumber = givenCargo();
        String declarationNumber = "IMP-P-" + System.nanoTime();
        Map<String, Object> declaration = new LinkedHashMap<>();
        declaration.put("declarationNumber", declarationNumber);
        declaration.put("trackingNumber", trackingNumber);
        declaration.put("declaredAt", "2026-09-09T09:00:00Z");
        rest.post().uri(url("/customs-declarations"))
                .header("X-Auth-Username", "handler01")
                .contentType(MediaType.APPLICATION_JSON)
                .body(declaration).retrieve().toBodilessEntity();
        await().atMost(Duration.ofSeconds(30)).untilAsserted(() ->
                assertThat(rest.get().uri(url("/customs-declarations/" + declarationNumber))
                        .retrieve().toEntity(JsonMap.class).getStatusCode())
                        .isEqualTo(HttpStatus.OK));

        var body = request(trackingNumber, "CLAIM", "USNYC");
        body.put("consigneeName", "John Smith");
        var response = register(body);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(String.valueOf(response.getBody())).contains("PENDING");
    }

    @Test
    @DisplayName("H.8: 引取を記録したら「引取待ち」から消える（残りが読めないと数え直す）")
    void removesClaimedCargosFromAwaitingClaim() {
        String trackingNumber = givenCargo();
        register(request(trackingNumber, "UNLOAD", "USNYC"));
        givenCustomsCleared(trackingNumber);
        var claim = request(trackingNumber, "CLAIM", "USNYC");
        claim.put("consigneeName", "John Smith");
        register(claim);

        await().atMost(Duration.ofSeconds(30)).untilAsserted(() -> {
            var response = rest.get().uri(url("/awaiting-claim?unLocode=USNYC"))
                    .retrieve().toEntity(JsonMap.class);
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> items =
                    (List<Map<String, Object>>) response.getBody().get("items");
            assertThat(items).noneSatisfy(item ->
                    assertThat(item).containsEntry("trackingNumber", trackingNumber));
        });
    }

    /**
     * 通関を通した状態にする（US29・IT12 でガードを有効にした）。
     *
     * <p><b>ガードを緩めるのではなく、前提づくりを足す</b>のが正しい直し方である
     * （計画 R1）。IT12 でガードを有効にしたとき、既存の引取の検査が 409 で落ちた。</p>
     */
    private void givenCustomsCleared(String trackingNumber) {
        String declarationNumber = "IMP-IT-" + System.nanoTime();
        Map<String, Object> declaration = new LinkedHashMap<>();
        declaration.put("declarationNumber", declarationNumber);
        declaration.put("trackingNumber", trackingNumber);
        declaration.put("declaredAt", "2026-09-09T09:00:00Z");
        rest.post().uri(url("/customs-declarations"))
                .header("X-Auth-Username", "handler01")
                .contentType(MediaType.APPLICATION_JSON)
                .body(declaration).retrieve().toBodilessEntity();
        await().atMost(Duration.ofSeconds(30)).untilAsserted(() ->
                assertThat(customs(declarationNumber).getStatusCode())
                        .isEqualTo(HttpStatus.OK));

        Map<String, Object> update = new LinkedHashMap<>();
        update.put("status", "CLEARED");
        update.put("reason", "書類に不備なし");
        rest.post().uri(url("/customs-declarations/" + declarationNumber + "/status"))
                .header("X-Auth-Username", "tracker01")
                .contentType(MediaType.APPLICATION_JSON)
                .body(update).retrieve().toBodilessEntity();
        await().atMost(Duration.ofSeconds(30)).untilAsserted(() ->
                assertThat(customs(declarationNumber).getBody())
                        .containsEntry("status", "CLEARED"));
    }

    private ResponseEntity<JsonMap> customs(String declarationNumber) {
        return rest.get().uri(url("/customs-declarations/" + declarationNumber))
                .header("X-Auth-Username", "tracker01")
                .retrieve().toEntity(JsonMap.class);
    }

}
