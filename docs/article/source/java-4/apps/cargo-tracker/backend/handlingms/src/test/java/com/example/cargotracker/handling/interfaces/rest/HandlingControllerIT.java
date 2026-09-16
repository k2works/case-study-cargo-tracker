package com.example.cargotracker.handling.interfaces.rest;

import java.math.BigDecimal;
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
 * 荷役の記録の HTTP（S50・S51 / US15）。
 *
 * <p><b>層が全部緑でも配線は未検査。</b> 予定ルート外の判定は application 層が
 * 解決してコマンドに載せる——集約の検査ではこの経路を判別しない。</p>
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class HandlingControllerIT extends AbstractAxonIntegrationTest {

    static class JsonMap extends LinkedHashMap<String, Object> {
        private static final long serialVersionUID = 1L;
    }

    @LocalServerPort
    private int port;

    @Autowired
    private CargoSnapshotProjection projection;

    private final RestClient rest = RestClient.builder()
            .defaultStatusHandler(status -> true, (request, response) -> { })
            .build();

    private String url(String path) {
        return "http://localhost:" + port + "/api/v1/handling" + path;
    }

    /** 追跡が始まっている貨物を作る（荷役はそのあとに起きる）。 */
    private String givenCargo() {
        String trackingNumber = "TRK-C" + System.nanoTime() % 1000000000L;
        projection.on(new TrackingInitializedEvent(trackingNumber, "b-" + System.nanoTime(),
                "SHP-000001", "JPTYO", "USNYC", "GENERAL",
                new BigDecimal("1200"),
                List.of(new TrackingInitializedEvent.Leg("V-MOL-001", "JPTYO", "SGSIN",
                                Instant.parse("2026-09-10T09:00:00Z"),
                                Instant.parse("2026-09-16T08:00:00Z")),
                        new TrackingInitializedEvent.Leg("V-ONE-002", "SGSIN", "USNYC",
                                Instant.parse("2026-09-17T06:00:00Z"),
                                Instant.parse("2026-09-24T18:00:00Z"))),
                Instant.parse("2026-09-08T01:00:00Z")), "evt-" + System.nanoTime());
        return trackingNumber;
    }

    /**
     * 通関を通した状態にする（US29・IT12 で引取のガードが有効になった）。
     *
     * <p><b>引取の検査には通関の前提が要る。</b> ガードを有効にした IT12 で、
     * 既存の引取のテストが 409 で落ちた（計画 R1 が言っていたとおり）。
     * 前提づくりを足すのが正しい直し方で、ガードを緩めるのは違う。</p>
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
                assertThat(rest.get().uri(url("/customs-declarations/" + declarationNumber))
                        .retrieve().toEntity(JsonMap.class).getStatusCode())
                        .isEqualTo(HttpStatus.OK));

        Map<String, Object> update = new LinkedHashMap<>();
        update.put("status", "CLEARED");
        update.put("reason", "書類に不備なし");
        rest.post().uri(url("/customs-declarations/" + declarationNumber + "/status"))
                .header("X-Auth-Username", "tracker01")
                .contentType(MediaType.APPLICATION_JSON)
                .body(update).retrieve().toBodilessEntity();
        await().atMost(Duration.ofSeconds(30)).untilAsserted(() ->
                assertThat(rest.get().uri(url("/customs-declarations/" + declarationNumber))
                        .retrieve().toEntity(JsonMap.class).getBody())
                        .containsEntry("status", "CLEARED"));
    }

    private ResponseEntity<JsonMap> register(Map<String, Object> body) {
        return rest.post().uri(url("/activities"))
                .header("X-Auth-Username", "handler01")
                .contentType(MediaType.APPLICATION_JSON)
                .body(body).retrieve().toEntity(JsonMap.class);
    }

    private static Map<String, Object> request(String trackingNumber, String type,
            String unLocode) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("activityId", "act-" + System.nanoTime());
        body.put("trackingNumber", trackingNumber);
        body.put("handlingType", type);
        body.put("unLocode", unLocode);
        if ("LOAD".equals(type) || "UNLOAD".equals(type)) {
            body.put("voyageNumber", "V-MOL-001");
        }
        return body;
    }

    @Test
    @DisplayName("US15 §1〜§4: 追跡番号で特定して記録できる")
    void registersHandling() {
        String trackingNumber = givenCargo();

        var response = register(request(trackingNumber, "RECEIVE", "JPTYO"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        await().atMost(Duration.ofSeconds(30)).untilAsserted(() -> {
            var history = rest.get().uri(url("/" + trackingNumber + "/activities"))
                    .retrieve().toEntity(JsonMap.class);
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> items =
                    (List<Map<String, Object>>) history.getBody().get("items");
            assertThat(items).hasSize(1);
            assertThat(items.get(0)).containsEntry("handlingTypeLabel", "受領");
            assertThat(items.get(0)).containsEntry("offRoute", false);
        });
    }

    @Test
    @DisplayName("US15 §7: 予定ルート外でも記録され、予定外の印が付く")
    void recordsOffRouteHandling() {
        // **判定は application 層が解決してコマンドに載せる。** 集約の検査では
        // この経路（CargoSnapshot を引いて isOffRoute を呼ぶ）を判別しない。
        String trackingNumber = givenCargo();

        var response = register(request(trackingNumber, "UNLOAD", "DEHAM"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        await().atMost(Duration.ofSeconds(30)).untilAsserted(() -> {
            var history = rest.get().uri(url("/" + trackingNumber + "/activities"))
                    .retrieve().toEntity(JsonMap.class);
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> items =
                    (List<Map<String, Object>>) history.getBody().get("items");
            assertThat(items).hasSize(1);
            assertThat(items.get(0)).containsEntry("offRoute", true);
        });
    }

    @Test
    @DisplayName("US15 §6: 存在しない追跡番号は記録できない")
    void rejectsUnknownTrackingNumber() {
        // 記録できたつもりで次へ進まれると、その貨物の事実がどこにも残らない。
        var response = register(request("TRK-NOSUCHNUM", "RECEIVE", "JPTYO"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.valueOf(422));
    }

    @Test
    @DisplayName("S50: この航海がこの港で降ろす貨物を引ける（記録済みも分かる）")
    void listsCargosOnVoyage() {
        String trackingNumber = givenCargo();

        var before = rest.get().uri(url("/voyages/V-MOL-001/cargos?unLocode=SGSIN"))
                .retrieve().toEntity(JsonMap.class);
        assertThat(before.getStatusCode()).isEqualTo(HttpStatus.OK);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> items =
                (List<Map<String, Object>>) before.getBody().get("items");
        assertThat(items).extracting(item -> item.get("trackingNumber")).contains(trackingNumber);
        assertThat(items.stream()
                .filter(item -> trackingNumber.equals(item.get("trackingNumber")))
                .findFirst().orElseThrow())
                .as("まだ記録していないので残り")
                .containsEntry("handledTypes", List.of());
    }

    @Test
    @DisplayName("S50 の確認欄: 追跡番号から予約と端点を引ける")
    void servesTheCargoSnapshot() {
        String trackingNumber = givenCargo();

        var response = rest.get().uri(url("/cargos/" + trackingNumber))
                .retrieve().toEntity(JsonMap.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).containsEntry("originUnLocode", "JPTYO");
        assertThat(response.getBody()).containsEntry("destinationUnLocode", "USNYC");
    }

    @Test
    @DisplayName("S50 の確認欄: 知らない追跡番号は 404")
    void returnsNotFoundForUnknownCargo() {
        assertThat(rest.get().uri(url("/cargos/TRK-NOSUCHNUM"))
                .retrieve().toBodilessEntity().getStatusCode())
                .isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    @DisplayName("不変条件 7: 取り消しても履歴から消えない（印が付く）")
    void voidsWithoutRemovingTheRecord() {
        String trackingNumber = givenCargo();
        var body = request(trackingNumber, "RECEIVE", "JPTYO");
        register(body);
        String activityId = String.valueOf(body.get("activityId"));

        var voided = rest.post().uri(url("/activities/" + activityId + "/void"))
                .header("X-Auth-Username", "handler01")
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("reason", "取り違えました"))
                .retrieve().toBodilessEntity();

        assertThat(voided.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        await().atMost(Duration.ofSeconds(30)).untilAsserted(() -> {
            var history = rest.get().uri(url("/" + trackingNumber + "/activities"))
                    .retrieve().toEntity(JsonMap.class);
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> items =
                    (List<Map<String, Object>>) history.getBody().get("items");
            assertThat(items).hasSize(1);
            assertThat(items.get(0)).containsEntry("voided", true);
            assertThat(items.get(0)).containsEntry("voidReason", "取り違えました");
            // **誰がいつ取り消したかが読めないと突き合わせられない**（M13）。
            // 契約イベントは voidedBy を運んでいるのに、投影が捨てていた。
            assertThat(items.get(0)).containsEntry("voidedBy", "handler01");
            assertThat(items.get(0)).containsKey("voidedAt");
        });
    }

    @Test
    @DisplayName("S50: 記録すると「残り」から外れる（連続記録で何本残っているか読む）")
    void marksHandledCargos() {
        String trackingNumber = givenCargo();
        register(request(trackingNumber, "UNLOAD", "SGSIN"));

        await().atMost(Duration.ofSeconds(30)).untilAsserted(() -> {
            var response = rest.get().uri(url("/voyages/V-MOL-001/cargos?unLocode=SGSIN"))
                    .retrieve().toEntity(JsonMap.class);
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> items =
                    (List<Map<String, Object>>) response.getBody().get("items");
            assertThat(items.stream()
                    .filter(item -> trackingNumber.equals(item.get("trackingNumber")))
                    .findFirst().orElseThrow())
                    // **種別で区別する**（M14）。引取が入ると同じ港で荷降し → 引取が
                    // 起きる。荷降しを済ませただけで引取まで済んだように見えると、
                    // その貨物は誰にも引き取られないまま「済」になる。
                    .containsEntry("handledTypes", List.of("UNLOAD"));
        });
    }

    @Test
    @DisplayName("S50: 同じ港の荷降しと引取を区別する（M14。引取が入ると同じ港で 2 度作業する）")
    void distinguishesHandledTypesAtTheSamePort() {
        String trackingNumber = givenCargo();
        register(request(trackingNumber, "UNLOAD", "SGSIN"));

        await().atMost(Duration.ofSeconds(30)).untilAsserted(() -> {
            var response = rest.get().uri(url("/voyages/V-MOL-001/cargos?unLocode=SGSIN"))
                    .retrieve().toEntity(JsonMap.class);
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> items =
                    (List<Map<String, Object>>) response.getBody().get("items");
            @SuppressWarnings("unchecked")
            List<String> handled = (List<String>) items.stream()
                    .filter(item -> trackingNumber.equals(item.get("trackingNumber")))
                    .findFirst().orElseThrow().get("handledTypes");
            assertThat(handled).containsExactly("UNLOAD").doesNotContain("CLAIM");
        });
    }

    @Test
    @DisplayName("H.8: 目的港で荷降し済・引取未記録の貨物が「引取待ち」に出る")
    void listsCargosAwaitingClaim() {
        // **荷役作業員の 2 つ目の入口**（下部タブ）。航海起点では引取に辿り着けない
        // ——引取は船から降りたあとの作業で、どの航海の仕事でもない。
        String trackingNumber = givenCargo();
        register(request(trackingNumber, "UNLOAD", "USNYC"));

        await().atMost(Duration.ofSeconds(30)).untilAsserted(() -> {
            var response = rest.get().uri(url("/awaiting-claim?unLocode=USNYC"))
                    .retrieve().toEntity(JsonMap.class);
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> items =
                    (List<Map<String, Object>>) response.getBody().get("items");
            assertThat(items).anySatisfy(item ->
                    assertThat(item).containsEntry("trackingNumber", trackingNumber));
        });
    }

    @Test
    @DisplayName("H.8: 荷降ししていない貨物は「引取待ち」に出ない（まだ船の上にある）")
    void doesNotListCargosNotYetUnloaded() {
        String trackingNumber = givenCargo();

        var response = rest.get().uri(url("/awaiting-claim?unLocode=USNYC"))
                .retrieve().toEntity(JsonMap.class);

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> items =
                (List<Map<String, Object>>) response.getBody().get("items");
        assertThat(items).noneSatisfy(item ->
                assertThat(item).containsEntry("trackingNumber", trackingNumber));
    }

    @Test
    @DisplayName("H.5: 予定外かどうかはサーバが答える（画面に判定を書き直させない）")
    void answersOffRouteForTheGivenPort() {
        String trackingNumber = givenCargo();

        var atOrigin = rest.get()
                .uri(url("/cargos/" + trackingNumber + "?unLocode=JPTYO"))
                .retrieve().toEntity(JsonMap.class);

        @SuppressWarnings("unchecked")
        Map<String, Object> offRoute = (Map<String, Object>) atOrigin.getBody().get("offRouteByType");
        assertThat(offRoute)
                .as("出発港での受領は予定どおり")
                .containsEntry("RECEIVE", false);
        assertThat(offRoute)
                .as("出発港での引取は予定外（目的港ではない）")
                .containsEntry("CLAIM", true);
    }

    @Test
    @DisplayName("H.5: 港を渡さなければ判定は返さない（S51 は港を持たない）")
    void omitsOffRouteWithoutAPort() {
        String trackingNumber = givenCargo();

        var response = rest.get().uri(url("/cargos/" + trackingNumber))
                .retrieve().toEntity(JsonMap.class);

        assertThat(response.getBody().get("offRouteByType")).isNull();
    }

    @Test
    @DisplayName("取り消した記録は「残り」に戻る（作業はやり直しになる）")
    void voidedActivityReturnsToTheRemaining() {
        String trackingNumber = givenCargo();
        var body = request(trackingNumber, "UNLOAD", "SGSIN");
        register(body);
        String activityId = String.valueOf(body.get("activityId"));

        rest.post().uri(url("/activities/" + activityId + "/void"))
                .header("X-Auth-Username", "handler01")
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("reason", "取り違えました"))
                .retrieve().toBodilessEntity();

        await().atMost(Duration.ofSeconds(30)).untilAsserted(() -> {
            var response = rest.get().uri(url("/voyages/V-MOL-001/cargos?unLocode=SGSIN"))
                    .retrieve().toEntity(JsonMap.class);
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> items =
                    (List<Map<String, Object>>) response.getBody().get("items");
            assertThat(items.stream()
                    .filter(item -> trackingNumber.equals(item.get("trackingNumber")))
                    .findFirst().orElseThrow())
                    .as("取り消したのに済んだままだと、その貨物が誰にも記録されない")
                    .containsEntry("handledTypes", List.of());
        });
    }

    @Test
    @DisplayName("知らない作業種別は 422（壊れたのではなく入力の誤り）")
    void rejectsUnknownHandlingType() {
        String trackingNumber = givenCargo();
        var body = request(trackingNumber, "RECEIVE", "JPTYO");
        body.put("handlingType", "CUSTOMS");

        assertThat(register(body).getStatusCode()).isEqualTo(HttpStatus.valueOf(422));
    }

    @Test
    @DisplayName("S02 荷役: 作業する航海と港を引ける（追跡番号を持たない現場の入口）")
    void listsVoyagePortsForTheDashboard() {
        // **件数だけでは仕事が進まない。** ここから S50 の航海起点に入る。
        String trackingNumber = givenCargo();

        await().atMost(Duration.ofSeconds(30)).untilAsserted(() -> {
            var response = rest.get().uri(url("/voyages"))
                    .retrieve().toEntity(JsonMap.class);
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> items =
                    (List<Map<String, Object>>) response.getBody().get("items");
            var sgsin = items.stream()
                    .filter(item -> "V-MOL-001".equals(item.get("voyageNumber"))
                            && "SGSIN".equals(item.get("unLocode")))
                    .findFirst();
            assertThat(sgsin).as("写しに入れた %s の区間が出ない", trackingNumber).isPresent();
            assertThat((Integer) sgsin.orElseThrow().get("cargoCount")).isPositive();
        });
    }

    @Test
    @DisplayName("不変条件 5: 同じ内容の記録が 5 分以内にあれば断る（読取機の二度打ち）")
    void rejectsRecentDuplicates() {
        // **冪等キーとは別の守り。** あちらは同じ送信の重複を、こちらは
        // 別々の送信で同じ内容の重複を断る（読取機の二度打ち、2 人が同じ貨物）。
        String trackingNumber = givenCargo();
        assertThat(register(request(trackingNumber, "RECEIVE", "JPTYO")).getStatusCode())
                .isEqualTo(HttpStatus.CREATED);
        await().atMost(Duration.ofSeconds(30)).untilAsserted(() -> {
            var history = rest.get().uri(url("/" + trackingNumber + "/activities"))
                    .retrieve().toEntity(JsonMap.class);
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> items =
                    (List<Map<String, Object>>) history.getBody().get("items");
            assertThat(items).hasSize(1);
        });

        // 別の activityId（＝別の送信）で同じ内容を送る。
        var second = register(request(trackingNumber, "RECEIVE", "JPTYO"));

        assertThat(second.getStatusCode()).isEqualTo(HttpStatus.valueOf(422));
        assertThat(String.valueOf(second.getBody().get("message"))).contains("5 分以内");
    }

    @Test
    @DisplayName("US16 §1・§2: 荷受人の確認なしの引取は断る（画面が外していても API は通っていた）")
    void rejectsClaimWithoutConsigneeConfirmation() {
        // **引取を通すと貨物状態が引取済——精算の開始条件——まで進み、戻せない。**
        String trackingNumber = givenCargo();

        var response = register(request(trackingNumber, "CLAIM", "USNYC"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.valueOf(422));
        assertThat(String.valueOf(response.getBody().get("message"))).contains("荷受人の確認");
    }

    @Test
    @DisplayName("US16 §2: 荷受人の確認を添えた引取は記録され、履歴に確認が出る")
    void registersClaimWithConsigneeConfirmation() {
        String trackingNumber = givenCargo();
        givenCustomsCleared(trackingNumber);
        var body = request(trackingNumber, "CLAIM", "USNYC");
        body.put("consigneeName", "John Smith");

        var response = register(body);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        await().atMost(Duration.ofSeconds(30)).untilAsserted(() -> {
            var history = rest.get().uri(url("/" + trackingNumber + "/activities"))
                    .retrieve().toEntity(JsonMap.class);
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> items =
                    (List<Map<String, Object>>) history.getBody().get("items");
            // **記録するだけでは誰にも見えない。** 読み口まで通っていることを見る。
            assertThat(items).anySatisfy(item -> {
                assertThat(item.get("handlingType")).isEqualTo("CLAIM");
                assertThat(item.get("consigneeName")).isEqualTo("John Smith");
            });
        });
    }

    @Test
    @DisplayName("荷受人の確認は引取以外に載せられない（黙って捨てない）")
    void rejectsConsigneeConfirmationOnOtherTypes() {
        String trackingNumber = givenCargo();
        var body = request(trackingNumber, "RECEIVE", "JPTYO");
        body.put("consigneeName", "John Smith");

        var response = register(body);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.valueOf(422));
    }
}
