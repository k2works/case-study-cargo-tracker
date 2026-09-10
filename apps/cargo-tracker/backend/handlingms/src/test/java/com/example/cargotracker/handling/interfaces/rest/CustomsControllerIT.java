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
 * 通関申告の HTTP（S52・S53 / US29）。
 *
 * <p><b>層が全部緑でも配線は未検査。</b> 「未決着は貨物あたり高々 1 件」
 * （不変条件 3）は集約から見えず application 層が守るので、集約の検査では
 * この経路を判別しない。</p>
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class CustomsControllerIT extends AbstractAxonIntegrationTest {

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

    /** 輸入港へ向かう貨物を作る（通関はそのあとに起きる）。 */
    private String givenCargo() {
        String trackingNumber = "TRK-D" + System.nanoTime() % 1000000000L;
        cargos.on(new TrackingInitializedEvent(trackingNumber, "b-" + System.nanoTime(),
                "SHP-000001", "JPTYO", "USNYC", "GENERAL",
                List.of(new TrackingInitializedEvent.Leg("V-MOL-001", "JPTYO", "USNYC",
                        Instant.parse("2026-09-10T09:00:00Z"),
                        Instant.parse("2026-09-16T08:00:00Z"))),
                Instant.parse("2026-09-08T01:00:00Z")), "evt-" + System.nanoTime());
        return trackingNumber;
    }

    private ResponseEntity<JsonMap> register(String number, String trackingNumber) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("declarationNumber", number);
        body.put("trackingNumber", trackingNumber);
        body.put("declaredAt", "2026-09-09T09:00:00Z");
        return rest.post().uri(url("/customs-declarations"))
                .header("X-Auth-Username", "handler01")
                .contentType(MediaType.APPLICATION_JSON)
                .body(body).retrieve().toEntity(JsonMap.class);
    }

    private ResponseEntity<JsonMap> updateStatus(String number, String status, String reason) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("status", status);
        if (reason != null) {
            body.put("reason", reason);
        }
        return rest.post().uri(url("/customs-declarations/" + number + "/status"))
                .header("X-Auth-Username", "tracker01")
                .contentType(MediaType.APPLICATION_JSON)
                .body(body).retrieve().toEntity(JsonMap.class);
    }

    private ResponseEntity<JsonMap> get(String path) {
        return rest.get().uri(url(path)).header("X-Auth-Username", "tracker01")
                .retrieve().toEntity(JsonMap.class);
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> items(ResponseEntity<JsonMap> response) {
        return (List<Map<String, Object>>) response.getBody().get("items");
    }

    @Test
    @DisplayName("US29 §1: 登録すると審査中で読み直せる")
    void registersDeclaration() {
        String trackingNumber = givenCargo();
        String number = "IMP-R-" + System.nanoTime();

        assertThat(register(number, trackingNumber).getStatusCode())
                .isEqualTo(HttpStatus.CREATED);

        await().atMost(Duration.ofSeconds(30)).untilAsserted(() ->
                assertThat(get("/customs-declarations/" + number).getBody())
                        .containsEntry("status", "PENDING")
                        .containsEntry("statusLabel", "審査中"));
    }

    @Test
    @DisplayName("US15 §6 と同じ扱い: 見つからない貨物には申告できない")
    void refusesUnknownCargo() {
        assertThat(register("IMP-U-" + System.nanoTime(), "TRK-NOTFOUND")
                .getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_CONTENT);
    }

    @Test
    @DisplayName("不変条件 3: 未決着の申告がある貨物には 2 件目を出せない")
    void refusesSecondUnsettledDeclaration() {
        // **集約では守れない。** 1 申告 1 集約なので、集約は他の申告を知らない。
        // 未決着が 2 件あると、引取のガードがどちらを見るかで結果が変わる。
        String trackingNumber = givenCargo();
        String first = "IMP-1-" + System.nanoTime();
        assertThat(register(first, trackingNumber).getStatusCode())
                .isEqualTo(HttpStatus.CREATED);
        await().atMost(Duration.ofSeconds(30)).untilAsserted(() ->
                assertThat(get("/customs-declarations/" + first).getStatusCode())
                        .isEqualTo(HttpStatus.OK));

        var second = register("IMP-2-" + System.nanoTime(), trackingNumber);

        assertThat(second.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_CONTENT);
        assertThat(String.valueOf(second.getBody())).contains(first);
    }

    @Test
    @DisplayName("不変条件 3: 決着していれば出し直せる（不可のあと）")
    void allowsNewDeclarationAfterRejected() {
        String trackingNumber = givenCargo();
        String first = "IMP-3-" + System.nanoTime();
        register(first, trackingNumber);
        await().atMost(Duration.ofSeconds(30)).untilAsserted(() ->
                assertThat(get("/customs-declarations/" + first).getStatusCode())
                        .isEqualTo(HttpStatus.OK));
        assertThat(updateStatus(first, "REJECTED", "通らなかった").getStatusCode())
                .isEqualTo(HttpStatus.OK);
        await().atMost(Duration.ofSeconds(30)).untilAsserted(() ->
                assertThat(get("/customs-declarations/" + first).getBody())
                        .containsEntry("status", "REJECTED"));

        assertThat(register("IMP-4-" + System.nanoTime(), trackingNumber).getStatusCode())
                .isEqualTo(HttpStatus.CREATED);
    }

    @Test
    @DisplayName("US29 §2: 理由の無い更新は入力の誤りとして断る")
    void refusesUpdateWithoutReason() {
        String number = "IMP-N-" + System.nanoTime();
        register(number, givenCargo());

        assertThat(updateStatus(number, "CLEARED", null).getStatusCode())
                .isEqualTo(HttpStatus.UNPROCESSABLE_CONTENT);
    }

    @Test
    @DisplayName("知らない通関状態は入力の誤りとして断る（壊れたのではない）")
    void refusesUnknownStatus() {
        String number = "IMP-X-" + System.nanoTime();
        register(number, givenCargo());

        assertThat(updateStatus(number, "SOMETHING", "理由").getStatusCode())
                .isEqualTo(HttpStatus.UNPROCESSABLE_CONTENT);
    }

    @Test
    @DisplayName("US29 §8: 変更履歴が日時・変更者・理由つきで読める")
    void readsHistoryFromTheProjection() {
        String number = "IMP-H-" + System.nanoTime();
        register(number, givenCargo());
        await().atMost(Duration.ofSeconds(30)).untilAsserted(() ->
                assertThat(get("/customs-declarations/" + number).getStatusCode())
                        .isEqualTo(HttpStatus.OK));
        updateStatus(number, "HELD", "原産地証明が未提出");
        updateStatus(number, "CLEARED", "証明書を受領");

        await().atMost(Duration.ofSeconds(30)).untilAsserted(() -> {
            var history = get("/customs-declarations/" + number + "/history");
            assertThat(items(history)).extracting(row -> row.get("reason"))
                    .contains("原産地証明が未提出", "証明書を受領");
            assertThat(items(history))
                    .filteredOn(row -> "STATUS_CHANGED".equals(row.get("kind")))
                    .allSatisfy(row -> {
                        assertThat(row.get("changedBy")).isNotNull();
                        assertThat(row.get("changedAt")).isNotNull();
                    });
            // **§4 の通知も履歴に出る。** 記録だけを積んで読み口を出さないと、
            // 受入基準の満たし方そのものが成り立たない。
            assertThat(items(history)).extracting(row -> row.get("kind"))
                    .contains("CLEARANCE_NOTIFIED");
        });
    }
}
