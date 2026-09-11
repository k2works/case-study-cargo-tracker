package com.example.cargotracker.acceptance.tracking;

import java.math.BigDecimal;
import com.example.cargotracker.shared.testing.AcceptanceFixtureTime;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import com.example.cargotracker.shared.contract.command.InitializeTrackingCommand;
import io.cucumber.java.ja.かつ;
import io.cucumber.java.ja.ならば;
import io.cucumber.java.ja.前提;
import io.cucumber.java.ja.もし;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.axonframework.messaging.commandhandling.gateway.CommandGateway;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestClient;

/**
 * 追跡の照会（US18）と状態の手動更新（US17）のステップ。
 *
 * <p>trackingms を実際に起動し、API を叩いて確かめる。集約や投影を直接呼ぶと
 * 「荷受人・追跡管理者から見てどうなるか」を判別できない。</p>
 */
public class TrackingSteps {

    static class JsonMap extends LinkedHashMap<String, Object> {
        private static final long serialVersionUID = 1L;
    }

    private static final String SHIPPER = "SHP-000001";

    @LocalServerPort
    private int trackingPort;

    @Autowired
    private CommandGateway commands;

    private final RestClient rest = RestClient.builder()
            .defaultStatusHandler(status -> true, (request, response) -> { })
            .build();

    private String trackingNumber;
    private ResponseEntity<JsonMap> lastResponse;

    private String url(String path) {
        return "http://localhost:" + trackingPort + path;
    }

    @前提("予約 {string} の追跡番号が発行され、追跡が始まっている")
    public void 追跡が始まっている(String bookingId) {
        trackingNumber = "TRK-B" + System.nanoTime() % 1000000000L;
        commands.sendAndWait(new InitializeTrackingCommand(trackingNumber,
                bookingId + "-" + System.nanoTime(), SHIPPER, "JPTYO", "USNYC", "GENERAL",
                // **日時は「今」から導く**（固定日付は現実の時刻に追い越される）。
                // 出発は昨日、到着は 2 週間後——追跡中の貨物という位置関係だけが要る。
                new BigDecimal("1200"),
                List.of(new InitializeTrackingCommand.LegDto("V-MOL-001", "JPTYO", "USNYC",
                        AcceptanceFixtureTime.at(-1, 9),
                        AcceptanceFixtureTime.at(13, 18))),
                AcceptanceFixtureTime.at(-3, 1)), String.class);

        // 投影は非同期に追いつく。
        await().atMost(Duration.ofSeconds(30)).untilAsserted(() ->
                assertThat(publicLookup(trackingNumber).getStatusCode()).isEqualTo(HttpStatus.OK));
    }

    private ResponseEntity<JsonMap> publicLookup(String number) {
        return rest.get().uri(url("/api/v1/tracking/public/" + number))
                .retrieve().toEntity(JsonMap.class);
    }

    @もし("追跡番号で照会する")
    public void 追跡番号で照会する() {
        lastResponse = publicLookup(trackingNumber);
    }

    @もし("存在しない追跡番号 {string} で照会する")
    public void 存在しない追跡番号で照会する(String number) {
        lastResponse = publicLookup(number);
    }

    @ならば("状態と区間と到着予定が読める")
    public void 状態と区間と到着予定が読める() {
        assertThat(lastResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(lastResponse.getBody())
                .containsEntry("statusLabel", "未受領")
                .containsEntry("originUnLocode", "JPTYO")
                .containsEntry("destinationUnLocode", "USNYC");
        assertThat(lastResponse.getBody().get("estimatedArrival"))
                .as("到着予定は予定の旅程の最終区間の荷降しから決まる")
                .isNotNull();
    }

    @かつ("認証は求められない")
    public void 認証は求められない() {
        // 上の照会は Authorization も X-Auth-* も送っていない。**荷受人はロールを
        // 持たない**ので、ここが 401 になると社外からは使えない。
        assertThat(lastResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @ならば("見つからないとだけ返る")
    public void 見つからないとだけ返る() {
        // 存在しない番号と権限の無い番号を区別しない。区別すると、総当たりで
        // 「実在するが自分のものではない番号」を選り分けられる。
        assertThat(lastResponse.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @ならば("応答に荷主 ID と予約 ID が入っていない")
    public void 応答に社内の情報が入っていない() {
        assertThat(lastResponse.getBody())
                .doesNotContainKey("shipperId")
                .doesNotContainKey("bookingId");
        assertThat(lastResponse.getBody().toString()).doesNotContain(SHIPPER);
    }

    @もし("追跡管理者が状態を {string} に更新する")
    public void 追跡管理者が状態を更新する(String newStatus) {
        lastResponse = rest.post()
                .uri(url("/api/v1/tracking/trackings/" + trackingNumber + "/status"))
                .header("X-Auth-Username", "tracker01")
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("newStatus", newStatus, "location", "JPTYO"))
                .retrieve().toEntity(JsonMap.class);
    }

    @ならば("現在の状態が {string} になる")
    public void 現在の状態になる(String statusLabel) {
        await().atMost(Duration.ofSeconds(30)).untilAsserted(() ->
                assertThat(detail(null).getBody()).containsEntry("statusLabel", statusLabel));
    }

    @かつ("現在の状態が {string} のままである")
    public void 現在の状態のままである(String statusLabel) {
        assertThat(detail(null).getBody()).containsEntry("statusLabel", statusLabel);
    }

    @かつ("履歴に手動更新として 1 件残る")
    public void 履歴に手動更新として残る() {
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> history =
                (List<Map<String, Object>>) detail(null).getBody().get("history");
        assertThat(history).hasSize(1);
        assertThat(history.get(0)).containsEntry("eventType", "MANUAL");
        assertThat(history.get(0)).containsEntry("recordedBy", "tracker01");
    }

    // ---- IT10 US19 例外（デモ項目 4〜9） ----

    private String exceptionId;

    @もし("追跡管理者が {string} の例外を起票する")
    public void 追跡管理者が例外を起票する(String type) {
        // **例外 ID はサーバが採番する**（IT10 レビュー N7）。起票の応答から拾う。
        lastResponse = rest.post()
                .uri(url("/api/v1/tracking/trackings/" + trackingNumber + "/exceptions"))
                .header("X-Auth-Username", "tracker01")
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("exceptionType", type,
                        "unLocode", "SGSIN", "description", "台風で 3 日遅れます"))
                .retrieve().toEntity(JsonMap.class);
        exceptionId = issuedExceptionId();
    }

    /**
     * 起票した例外の識別子。
     *
     * <p><b>応答が返さないなら一覧から拾う。</b> サーバ採番にしたので、
     * 呼ぶ側は自分で決めた ID を持たない。</p>
     */
    private String issuedExceptionId() {
        if (lastResponse.getBody() != null && lastResponse.getBody().get("exceptionId") != null) {
            return String.valueOf(lastResponse.getBody().get("exceptionId"));
        }
        await().atMost(Duration.ofSeconds(30)).untilAsserted(() ->
                assertThat(openExceptionIds()).isNotEmpty());
        return openExceptionIds().get(0);
    }

    @かつ("荷主へ知らせた記録を残す")
    public void 荷主へ知らせた記録を残す() {
        lastResponse = rest.post()
                .uri(url("/api/v1/tracking/trackings/" + trackingNumber
                        + "/exceptions/" + exceptionId + "/notifications"))
                .header("X-Auth-Username", "tracker01")
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("means", "電話", "summary", "3 日遅れる見込みと伝えました"))
                .retrieve().toEntity(JsonMap.class);
    }

    @かつ("その例外を解決する")
    public void その例外を解決する() {
        lastResponse = rest.post()
                .uri(url("/api/v1/tracking/trackings/" + trackingNumber
                        + "/exceptions/" + exceptionId + "/resolution"))
                .header("X-Auth-Username", "tracker01")
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("resolution", "代替便に振り替えました"))
                .retrieve().toEntity(JsonMap.class);
    }

    @ならば("未解決の例外一覧に出る")
    public void 未解決の例外一覧に出る() {
        await().atMost(Duration.ofSeconds(30)).untilAsserted(() ->
                assertThat(openExceptionIds()).contains(exceptionId));
    }

    @かつ("未解決の例外一覧から外れる")
    public void 未解決の例外一覧から外れる() {
        // **決着したものが混ざると、一覧全体が「まだ手を入れる場所」に見えなくなる。**
        await().atMost(Duration.ofSeconds(30)).untilAsserted(() ->
                assertThat(openExceptionIds()).doesNotContain(exceptionId));
    }

    @かつ("履歴に例外の起票が残る")
    public void 履歴に例外の起票が残る() {
        await().atMost(Duration.ofSeconds(30)).untilAsserted(() -> {
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> history =
                    (List<Map<String, Object>>) detail(null).getBody().get("history");
            assertThat(history).anySatisfy(item ->
                    assertThat(item).containsEntry("eventType", "EXCEPTION"));
        });
    }

    @かつ("解決した対応内容が残る")
    public void 解決した対応内容が残る() {
        // **解決しても事実は消えない**（不変条件 6）。料金調整の根拠になる。
        await().atMost(Duration.ofSeconds(30)).untilAsserted(() -> {
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> exceptions =
                    (List<Map<String, Object>>) detail(null).getBody().get("exceptions");
            assertThat(exceptions).anySatisfy(item -> {
                assertThat(item).containsEntry("exceptionId", exceptionId);
                assertThat(item).containsEntry("resolution", "代替便に振り替えました");
            });
        });
    }

    // ---- IT11 US20 破損・紛失（デモ項目 D1〜D4） ----

    @もし("追跡管理者が発生場所 {string} で {string} の例外を起票する")
    public void 追跡管理者が発生場所で例外を起票する(String unLocode, String type) {
        lastResponse = rest.post()
                .uri(url("/api/v1/tracking/trackings/" + trackingNumber + "/exceptions"))
                .header("X-Auth-Username", "tracker01")
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("exceptionType", type,
                        "unLocode", unLocode, "description", "外装が破れています"))
                .retrieve().toEntity(JsonMap.class);
    }

    @ならば("港コードではないと断られる")
    public void 港コードではないと断られる() {
        // **理由まで見る。** 2xx でないことだけを見ると、別の理由で断られていても緑になる。
        assertThat(lastResponse.getStatusCode()).isEqualTo(HttpStatus.valueOf(422));
        assertThat(lastResponse.getBody().get("message").toString())
                .contains("UN/LOCODE");
    }

    @ならば("その例外は緊急として記録される")
    public void その例外は緊急として記録される() {
        // **緊急かどうかは種別が答える**（不変条件 7）。起票した人は選べない。
        await().atMost(Duration.ofSeconds(30)).untilAsserted(() ->
                assertThat(openException(exceptionId)).containsEntry("urgent", true));
    }

    @かつ("上位者へ知らせた記録が残る")
    public void 上位者へ知らせた記録が残る() {
        // **記録と読み口は対で出す**（US20 §受入基準 3）。送信基盤はスコープ外なので、
        // 残るのは「いつ知らせたか」だけ。一覧がそれを出せて初めて、管理者は
        // 自分が見るべきものを見つけられる。
        await().atMost(Duration.ofSeconds(30)).untilAsserted(() ->
                assertThat(openException(exceptionId).get("escalatedAt")).isNotNull());
    }

    @かつ("未解決の例外一覧の先頭に出る")
    public void 未解決の例外一覧の先頭に出る() {
        // **並びはサーバが決める**（緊急が先。不変条件 7）。
        await().atMost(Duration.ofSeconds(30)).untilAsserted(() ->
                assertThat(openExceptionIds()).first().isEqualTo(exceptionId));
    }

    private Map<String, Object> openException(String id) {
        var response = rest.get().uri(url("/api/v1/tracking/trackings/exceptions"))
                .header("X-Auth-Username", "tracker01")
                .retrieve().toEntity(JsonMap.class);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> items =
                (List<Map<String, Object>>) response.getBody().get("items");
        return items.stream()
                .filter(item -> id.equals(item.get("exceptionId")))
                .findFirst()
                .orElseThrow(() -> new AssertionError("例外 " + id + " が一覧に出ていません"));
    }

    private List<String> openExceptionIds() {
        var response = rest.get().uri(url("/api/v1/tracking/trackings/exceptions"))
                .header("X-Auth-Username", "tracker01")
                .retrieve().toEntity(JsonMap.class);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> items =
                (List<Map<String, Object>>) response.getBody().get("items");
        return items.stream().map(item -> String.valueOf(item.get("exceptionId"))).toList();
    }

    @ならば("断られる")
    public void 断られる() {
        // **状態コードと理由まで見る。** 2xx でないことだけを見ると、404 でも
        // 500 でも緑になり「遷移表が許さないから断った」ことを判別しない。
        assertThat(lastResponse.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(lastResponse.getBody().get("message").toString())
                .contains("未受領")
                .contains("荷降し済");
    }

    @ならば("手では入れられないと断られる")
    public void 手では入れられないと断られる() {
        // **引取済は手で選べない**（IT10 レビュー 高）。手で動かすと
        // CargoDeliveredEvent が出ず、精算も予約の配送完了も始まらない。
        assertThat(lastResponse.getStatusCode()).isEqualTo(HttpStatus.valueOf(422));
        assertThat(lastResponse.getBody().get("message").toString())
                .contains("手では入れられません");
    }

    @ならば("誤配は自動で起票されると断られる")
    public void 誤配は自動で起票されると断られる() {
        // **誤配は荷役が決める**（US28 §受入基準 2）。手で起票できると、
        // 起きていない誤配を記録でき、経路設計者はそれを組み直そうとする。
        assertThat(lastResponse.getStatusCode()).isEqualTo(HttpStatus.valueOf(422));
        assertThat(lastResponse.getBody().get("message").toString())
                .contains("システムが起票します");
    }

    @もし("別の荷主として追跡を開く")
    public void 別の荷主として追跡を開く() {
        lastResponse = detail("SHP-000999");
    }

    /**
     * 追跡詳細を開く。<b>Gateway が実際に送る形</b>で送る。
     *
     * <p>ロールと荷主 ID は Gateway が JWT から取り出して付ける。片方だけを
     * 送る検査にすると、本番では起きない組合せで緑になる。</p>
     */
    private ResponseEntity<JsonMap> detail(String shipperId) {
        var request = rest.get().uri(url("/api/v1/tracking/trackings/" + trackingNumber))
                .header("X-Auth-Roles", shipperId == null ? "ROLE_TRACKER" : "ROLE_SHIPPER");
        if (shipperId != null) {
            request = request.header("X-Auth-Shipper-Id", shipperId);
        }
        return request.retrieve().toEntity(JsonMap.class);
    }
}
