package com.example.cargotracker.acceptance.handling;

import com.example.cargotracker.shared.testing.AcceptanceFixtureTime;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import com.example.cargotracker.handling.infrastructure.projection.CargoSnapshotProjection;
import com.example.cargotracker.shared.contract.event.TrackingInitializedEvent;
import io.cucumber.java.ja.かつ;
import io.cucumber.java.ja.ならば;
import io.cucumber.java.ja.前提;
import io.cucumber.java.ja.もし;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestClient;

/**
 * 荷役の記録（US15）のステップ。
 *
 * <p>handlingms を実際に起動し、API を叩いて確かめる。集約や投影を直接呼ぶと
 * 「荷役作業員から見てどうなるか」を判別できない。</p>
 */
public class HandlingSteps {

    static class JsonMap extends LinkedHashMap<String, Object> {
        private static final long serialVersionUID = 1L;
    }

    private static final String VOYAGE = "V-MOL-001";
    private static final String PORT = "SGSIN";

    @LocalServerPort
    private int port;

    @Autowired
    private CargoSnapshotProjection projection;

    private final RestClient rest = RestClient.builder()
            .defaultStatusHandler(status -> true, (request, response) -> { })
            .build();

    private String trackingNumber;
    private String activityId;
    private ResponseEntity<JsonMap> lastResponse;

    private String url(String path) {
        return "http://localhost:" + port + "/api/v1/handling" + path;
    }

    @前提("航海 {string} が {string} で降ろす貨物がある")
    public void 航海が降ろす貨物がある(String voyage, String unLocode) {
        trackingNumber = "TRK-F" + System.nanoTime() % 1000000000L;
        projection.on(new TrackingInitializedEvent(trackingNumber, "b-" + System.nanoTime(),
                "SHP-000001", "JPTYO", "USNYC", "GENERAL",
                // **日時は「今」から導く**（固定日付は現実の時刻に追い越される）。
                List.of(new TrackingInitializedEvent.Leg(voyage, "JPTYO", unLocode,
                        AcceptanceFixtureTime.at(-1, 9),
                        AcceptanceFixtureTime.at(5, 8))),
                AcceptanceFixtureTime.at(-3, 1)), "evt-" + System.nanoTime());
    }

    @もし("航海と港で貨物を探す")
    public void 航海と港で貨物を探す() {
        lastResponse = rest.get().uri(url("/voyages/" + VOYAGE + "/cargos?unLocode=" + PORT))
                .retrieve().toEntity(JsonMap.class);
    }

    @ならば("その貨物が一覧に出る")
    public void その貨物が一覧に出る() {
        assertThat(lastResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(items(lastResponse)).extracting(item -> item.get("trackingNumber"))
                .contains(trackingNumber);
    }

    @かつ("まだ記録していないことが分かる")
    public void まだ記録していないことが分かる() {
        assertThat(itemOf(lastResponse, trackingNumber))
                .containsEntry("handledTypes", java.util.List.of());
    }

    private ResponseEntity<JsonMap> register(String type, String unLocode, String number,
            Instant completedAt) {
        activityId = "act-" + System.nanoTime();
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("activityId", activityId);
        body.put("trackingNumber", number);
        body.put("handlingType", type);
        body.put("unLocode", unLocode);
        body.put("voyageNumber", VOYAGE);
        if (completedAt != null) {
            body.put("completedAt", completedAt.toString());
        }
        return rest.post().uri(url("/activities"))
                .header("X-Auth-Username", "handler01")
                .contentType(MediaType.APPLICATION_JSON)
                .body(body).retrieve().toEntity(JsonMap.class);
    }

    @もし("荷降しを記録する")
    public void 荷降しを記録する() {
        lastResponse = register("UNLOAD", PORT, trackingNumber, null);
        assertThat(lastResponse.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    }

    @もし("予定にない港 {string} で荷降しを記録する")
    public void 予定にない港で荷降しを記録する(String unLocode) {
        lastResponse = register("UNLOAD", unLocode, trackingNumber, null);
        assertThat(lastResponse.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    }

    @もし("同じ活動 ID で二度記録する")
    public void 同じ活動IDで二度記録する() {
        lastResponse = register("UNLOAD", PORT, trackingNumber, null);
        String sameId = activityId;
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("activityId", sameId);
        body.put("trackingNumber", trackingNumber);
        body.put("handlingType", "UNLOAD");
        body.put("unLocode", PORT);
        body.put("voyageNumber", VOYAGE);
        rest.post().uri(url("/activities"))
                .header("X-Auth-Username", "handler01")
                .contentType(MediaType.APPLICATION_JSON)
                .body(body).retrieve().toBodilessEntity();
    }

    @もし("別の活動 ID で同じ内容を続けて記録する")
    public void 別の活動IDで同じ内容を続けて記録する() {
        // **冪等キーとは別の守り。** 読取機の二度打ちや、2 人が同じ貨物を
        // 記録したときを断る（不変条件 5）。
        assertThat(register("UNLOAD", PORT, trackingNumber, null).getStatusCode())
                .isEqualTo(HttpStatus.CREATED);
        await().atMost(Duration.ofSeconds(30)).untilAsserted(() ->
                assertThat(history()).hasSize(1));

        lastResponse = register("UNLOAD", PORT, trackingNumber, null);
    }

    @もし("未来の日時で記録する")
    public void 未来の日時で記録する() {
        lastResponse = register("UNLOAD", PORT, trackingNumber,
                Instant.now().plusSeconds(86400));
    }

    @もし("存在しない追跡番号 {string} で記録する")
    public void 存在しない追跡番号で記録する(String number) {
        lastResponse = register("UNLOAD", PORT, number, null);
    }

    @ならば("記録が履歴に残る")
    public void 記録が履歴に残る() {
        await().atMost(Duration.ofSeconds(30)).untilAsserted(() ->
                assertThat(history()).hasSize(1));
    }

    @かつ("一覧で記録済みになる")
    public void 一覧で記録済みになる() {
        await().atMost(Duration.ofSeconds(30)).untilAsserted(() -> {
            var listed = rest.get().uri(url("/voyages/" + VOYAGE + "/cargos?unLocode=" + PORT))
                    .retrieve().toEntity(JsonMap.class);
            assertThat(itemOf(listed, trackingNumber))
                    .containsEntry("handledTypes", java.util.List.of("UNLOAD"));
        });
    }

    @かつ("予定外の印が付く")
    public void 予定外の印が付く() {
        await().atMost(Duration.ofSeconds(30)).untilAsserted(() ->
                assertThat(history().get(0)).containsEntry("offRoute", true));
    }

    @ならば("履歴は 1 件のままである")
    public void 履歴は1件のままである() {
        await().atMost(Duration.ofSeconds(30)).untilAsserted(() ->
                assertThat(history()).hasSize(1));
    }

    // ---- IT10 US16 引取（デモ項目 1・2） ----

    @もし("荷受人の確認なしで引取を記録する")
    public void 荷受人の確認なしで引取を記録する() {
        lastResponse = register("CLAIM", "USNYC", trackingNumber, null);
    }

    /**
     * 通関を通した状態にする（US29・IT12 でガードを有効にした）。
     *
     * <p><b>ここに置く。</b> ステップ定義はクラスごとに別の状態を持つので、
     * 通関側のクラスから追跡番号を見られない。<b>ガードを緩めるのではなく、
     * 前提づくりを足す</b>のが正しい直し方である（計画 R1）。</p>
     */
    @かつ("その貨物の通関が済んでいる")
    public void その貨物の通関が済んでいる() {
        String declarationNumber = "IMP-PRE-" + System.nanoTime();
        Map<String, Object> declaration = new LinkedHashMap<>();
        declaration.put("declarationNumber", declarationNumber);
        declaration.put("trackingNumber", trackingNumber);
        // 申告は過去の出来事（2 日前）。留置営業日の起点になるので未来にしない。
        declaration.put("declaredAt", AcceptanceFixtureTime.at(-2, 9).toString());
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

    @もし("荷受人の確認を添えて引取を記録する")
    public void 荷受人の確認を添えて引取を記録する() {
        activityId = "act-" + System.nanoTime();
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("activityId", activityId);
        body.put("trackingNumber", trackingNumber);
        body.put("handlingType", "CLAIM");
        body.put("unLocode", "USNYC");
        body.put("consigneeName", "John Smith");
        lastResponse = rest.post().uri(url("/activities"))
                .header("X-Auth-Username", "handler01")
                .contentType(MediaType.APPLICATION_JSON)
                .body(body).retrieve().toEntity(JsonMap.class);
        assertThat(lastResponse.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    }

    @かつ("履歴に荷受人の確認が残る")
    public void 履歴に荷受人の確認が残る() {
        // **記録するだけでは誰にも見えない。** 読み口まで通っていることを見る。
        await().atMost(Duration.ofSeconds(30)).untilAsserted(() ->
                assertThat(history()).anySatisfy(item -> {
                    assertThat(item).containsEntry("handlingType", "CLAIM");
                    assertThat(item).containsEntry("consigneeName", "John Smith");
                }));
    }

    @ならば("断られる")
    public void 断られる() {
        // **状態コードまで見る。** 2xx でないことだけを見ると、404 でも 500 でも
        // 緑になり「業務の規則で断った」ことを判別しない。
        assertThat(lastResponse.getStatusCode()).isEqualTo(HttpStatus.valueOf(422));
    }

    @かつ("その記録を取り消す")
    public void その記録を取り消す() {
        rest.post().uri(url("/activities/" + activityId + "/void"))
                .header("X-Auth-Username", "handler01")
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("reason", "取り違えました"))
                .retrieve().toBodilessEntity();
    }

    @ならば("履歴に取り消しの印と理由が残る")
    public void 履歴に取り消しの印と理由が残る() {
        await().atMost(Duration.ofSeconds(30)).untilAsserted(() -> {
            var items = history();
            assertThat(items).hasSize(1);
            assertThat(items.get(0)).containsEntry("voided", true);
            assertThat(items.get(0)).containsEntry("voidReason", "取り違えました");
        });
    }

    @かつ("一覧で未記録に戻る")
    public void 一覧で未記録に戻る() {
        await().atMost(Duration.ofSeconds(30)).untilAsserted(() -> {
            var listed = rest.get().uri(url("/voyages/" + VOYAGE + "/cargos?unLocode=" + PORT))
                    .retrieve().toEntity(JsonMap.class);
            assertThat(itemOf(listed, trackingNumber))
                    .containsEntry("handledTypes", java.util.List.of());
        });
    }

    private List<Map<String, Object>> history() {
        var response = rest.get().uri(url("/" + trackingNumber + "/activities"))
                .retrieve().toEntity(JsonMap.class);
        return items(response);
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> items(ResponseEntity<JsonMap> response) {
        return (List<Map<String, Object>>) response.getBody().get("items");
    }

    private static Map<String, Object> itemOf(ResponseEntity<JsonMap> response, String number) {
        return items(response).stream()
                .filter(item -> number.equals(item.get("trackingNumber")))
                .findFirst().orElseThrow();
    }
}
