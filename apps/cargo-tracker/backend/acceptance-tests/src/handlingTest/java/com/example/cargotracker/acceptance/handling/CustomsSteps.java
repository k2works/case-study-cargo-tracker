package com.example.cargotracker.acceptance.handling;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.cargotracker.handling.infrastructure.projection.CargoSnapshotProjection;
import com.example.cargotracker.shared.contract.event.TrackingInitializedEvent;
import io.cucumber.java.ja.かつ;
import io.cucumber.java.ja.ならば;
import io.cucumber.java.ja.前提;
import io.cucumber.java.ja.もし;
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
 * 通関申告（US29）のステップ。
 *
 * <p>handlingms を実際に起動し、API を叩いて確かめる。集約や投影を直接呼ぶと
 * 「荷役作業員・追跡管理者から見てどうなるか」を判別できない。</p>
 *
 * <p><b>実装より先に置く</b>（終盤の Phase 1）。置いた時点では通関のエンドポイントが
 * 無いので、すべて 404 で赤になる。緑にしていくのが本 IT の仕事である。</p>
 */
public class CustomsSteps {

    static class JsonMap extends LinkedHashMap<String, Object> {
        private static final long serialVersionUID = 1L;
    }

    /** 輸入通関のみを扱う（不変条件 5）。目的港が申告の場所になる。 */
    private static final String IMPORT_PORT = "USNYC";

    @LocalServerPort
    private int port;

    @Autowired
    private CargoSnapshotProjection snapshots;

    private final RestClient rest = RestClient.builder()
            .defaultStatusHandler(status -> true, (request, response) -> { })
            .build();

    private String trackingNumber;
    private String declarationNumber;
    private ResponseEntity<JsonMap> lastResponse;

    private String url(String path) {
        return "http://localhost:" + port + "/api/v1/handling" + path;
    }

    @前提("輸入港 {string} へ向かう貨物がある")
    public void 輸入港へ向かう貨物がある(String unLocode) {
        trackingNumber = "TRK-C" + System.nanoTime() % 1000000000L;
        snapshots.on(new TrackingInitializedEvent(trackingNumber, "b-" + System.nanoTime(),
                "SHP-000001", "JPTYO", unLocode, "GENERAL",
                List.of(new TrackingInitializedEvent.Leg("V-MOL-001", "JPTYO", unLocode,
                        Instant.parse("2026-09-10T09:00:00Z"),
                        Instant.parse("2026-09-16T08:00:00Z"))),
                Instant.parse("2026-09-08T01:00:00Z")), "evt-" + System.nanoTime());
    }

    @もし("申告番号 {string} 申告日時 {string} で通関申告を登録する")
    public void 通関申告を登録する(String number, String declaredAt) {
        // **申告番号は利用者が持ち込む**（税関が採番する）。同時に走る受け入れが
        // 衝突しないよう、シナリオの番号に一意な尾を付ける。
        declarationNumber = number + "-" + System.nanoTime() % 1000000L;
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("declarationNumber", declarationNumber);
        body.put("trackingNumber", trackingNumber);
        body.put("declaredAt", declaredAt);
        lastResponse = rest.post().uri(url("/customs-declarations"))
                .header("X-Auth-Username", "handler01")
                .contentType(MediaType.APPLICATION_JSON)
                .body(body).retrieve().toEntity(JsonMap.class);
    }

    @もし("理由 {string} で通関状態を {string} に更新する")
    public void 通関状態を更新する(String reason, String status) {
        lastResponse = updateStatus(status, reason);
    }

    @もし("理由を入れずに通関状態を {string} に更新する")
    public void 理由を入れずに通関状態を更新する(String status) {
        lastResponse = updateStatus(status, null);
    }

    private ResponseEntity<JsonMap> updateStatus(String status, String reason) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("status", statusCodeOf(status));
        if (reason != null) {
            body.put("reason", reason);
        }
        return rest.post()
                .uri(url("/customs-declarations/" + declarationNumber + "/status"))
                .header("X-Auth-Username", "tracker01")
                .contentType(MediaType.APPLICATION_JSON)
                .body(body).retrieve().toEntity(JsonMap.class);
    }

    /** 画面の言葉を列挙の名前へ。**知らない言葉はそのまま送る**（サーバが断る）。 */
    private static String statusCodeOf(String label) {
        return switch (label) {
            case "審査中" -> "PENDING";
            case "通関済" -> "CLEARED";
            case "留置" -> "HELD";
            case "不可" -> "REJECTED";
            default -> label;
        };
    }

    @もし("その貨物の引取を記録する")
    public void その貨物の引取を記録する() {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("activityId", "act-" + System.nanoTime());
        body.put("trackingNumber", trackingNumber);
        body.put("handlingType", "CLAIM");
        body.put("unLocode", IMPORT_PORT);
        body.put("consigneeConfirmation", "受領確認コード 1234");
        lastResponse = rest.post().uri(url("/activities"))
                .header("X-Auth-Username", "handler01")
                .contentType(MediaType.APPLICATION_JSON)
                .body(body).retrieve().toEntity(JsonMap.class);
    }

    @もし("留置から {int} 営業日が過ぎた")
    public void 留置から営業日が過ぎた(int days) {
        // 時計を進める手段は実装時に決める（BusinessClock の差し替え）。
        // ここでは「何を確かめたいか」だけを先に置く。
        lastResponse = rest.post()
                .uri(url("/customs-declarations/" + declarationNumber
                        + "/test-support/advance-business-days?days=" + days))
                .header("X-Auth-Username", "tracker01")
                .retrieve().toEntity(JsonMap.class);
    }

    @もし("通関申告の一覧を取る")
    public void 通関申告の一覧を取る() {
        lastResponse = list("");
    }

    @もし("通関済も含めて一覧を取る")
    public void 通関済も含めて一覧を取る() {
        lastResponse = list("?includeCleared=true");
    }

    @もし("通関状態 {string} で一覧を絞る")
    public void 通関状態で一覧を絞る(String status) {
        lastResponse = list("?includeCleared=true&status=" + statusCodeOf(status));
    }

    @もし("その申告の履歴を取る")
    public void その申告の履歴を取る() {
        lastResponse = rest.get()
                .uri(url("/customs-declarations/" + declarationNumber + "/history"))
                .header("X-Auth-Username", "tracker01")
                .retrieve().toEntity(JsonMap.class);
    }

    private ResponseEntity<JsonMap> list(String query) {
        return rest.get().uri(url("/customs-declarations" + query))
                .header("X-Auth-Username", "tracker01")
                .retrieve().toEntity(JsonMap.class);
    }

    @ならば("その操作は成功する")
    public void その操作は成功する() {
        assertThat(lastResponse.getStatusCode().is2xxSuccessful())
                .as("応答: %s %s", lastResponse.getStatusCode(), lastResponse.getBody())
                .isTrue();
    }

    @ならば("その操作は入力の誤りとして断られる")
    public void その操作は入力の誤りとして断られる() {
        assertThat(lastResponse.getStatusCode())
                .as("応答本文: %s", lastResponse.getBody())
                .isEqualTo(HttpStatus.UNPROCESSABLE_CONTENT);
    }

    @ならば("その操作は状態の誤りとして断られる")
    public void その操作は状態の誤りとして断られる() {
        assertThat(lastResponse.getStatusCode())
                .as("応答本文: %s", lastResponse.getBody())
                .isEqualTo(HttpStatus.CONFLICT);
    }

    @ならば("断りの理由に通関状態 {string} が含まれる")
    public void 断りの理由に通関状態が含まれる(String status) {
        // **拒否のときは判定に使った状態を返す**（domain-model.md）。
        // 画面が「直近で変わった可能性があります」と再確認へ導けるようにする。
        assertThat(String.valueOf(lastResponse.getBody()))
                .contains(statusCodeOf(status));
    }

    @ならば("その通関状態は {string} である")
    public void その通関状態である(String status) {
        var response = rest.get()
                .uri(url("/customs-declarations/" + declarationNumber))
                .header("X-Auth-Username", "tracker01")
                .retrieve().toEntity(JsonMap.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).containsEntry("status", statusCodeOf(status));
    }

    @ならば("その申告の履歴に通関完了の通知が {int} 件ある")
    public void 履歴に通関完了の通知がある(int count) {
        その申告の履歴を取る();
        assertThat(items(lastResponse))
                .filteredOn(row -> "CLEARANCE_NOTIFIED".equals(row.get("kind")))
                .hasSize(count);
    }

    @ならば("その申告は督促の対象として一覧に出る")
    public void 督促の対象として一覧に出る() {
        lastResponse = list("?overdueOnly=true");
        assertThat(numbersOf(lastResponse)).contains(declarationNumber);
    }

    @かつ("その申告の留置営業日数は {int} 日を超えている")
    public void 留置営業日数が超えている(int days) {
        assertThat(itemOf(lastResponse))
                .hasEntrySatisfying("heldBusinessDays",
                        value -> assertThat(((Number) value).intValue()).isGreaterThan(days));
    }

    @ならば("その申告は一覧に出ない")
    public void その申告は一覧に出ない() {
        assertThat(numbersOf(lastResponse)).doesNotContain(declarationNumber);
    }

    @ならば("その申告は一覧に出る")
    public void その申告は一覧に出る() {
        assertThat(numbersOf(lastResponse)).contains(declarationNumber);
    }

    @ならば("履歴には理由 {string} の行がある")
    public void 履歴に理由の行がある(String reason) {
        assertThat(items(lastResponse)).extracting(row -> row.get("reason")).contains(reason);
    }

    @かつ("履歴の各行には変更者と日時がある")
    public void 履歴の各行には変更者と日時がある() {
        assertThat(items(lastResponse)).allSatisfy(row -> {
            assertThat(row.get("changedBy")).as("誰が変えたか読めない履歴は根拠にならない")
                    .isNotNull();
            assertThat(row.get("changedAt")).as("いつ変わったか読めない履歴は根拠にならない")
                    .isNotNull();
        });
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> items(ResponseEntity<JsonMap> response) {
        assertThat(response.getBody()).as("応答: %s", response.getStatusCode()).isNotNull();
        return (List<Map<String, Object>>) response.getBody().get("items");
    }

    private static List<String> numbersOf(ResponseEntity<JsonMap> response) {
        return items(response).stream()
                .map(item -> String.valueOf(item.get("declarationNumber"))).toList();
    }

    private Map<String, Object> itemOf(ResponseEntity<JsonMap> response) {
        return items(response).stream()
                .filter(item -> declarationNumber.equals(item.get("declarationNumber")))
                .findFirst().orElseThrow();
    }
}
