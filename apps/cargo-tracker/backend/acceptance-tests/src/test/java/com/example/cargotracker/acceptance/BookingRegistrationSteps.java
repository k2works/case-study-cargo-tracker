package com.example.cargotracker.acceptance;

import static org.assertj.core.api.Assertions.assertThat;

import io.cucumber.java.ja.かつ;
import io.cucumber.java.ja.ならば;
import io.cucumber.java.ja.もし;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestClient;

/**
 * 貨物予約の登録（US04）のステップ。
 *
 * <p>bookingms を実際に起動し、API を叩いて確かめる。集約や投影を直接呼ぶと、
 * 「画面から見てどうなるか」を判別できない。</p>
 */
public class BookingRegistrationSteps {

    static class JsonMap extends LinkedHashMap<String, Object> {
        private static final long serialVersionUID = 1L;
    }

    @LocalServerPort
    private int bookingPort;

    private final RestClient rest = RestClient.builder()
            .defaultStatusHandler(status -> true, (request, response) -> { })
            .build();

    /**
     * 業務の言葉と API が返す値の対応（`ui_design.md`「付録：ステータスバッジ」）。
     *
     * <p>画面のラベルはフロントが持つ。ここでは受け入れテストが業務の言葉で書けて、
     * かつ実装を壊したら赤になるようにするために持つ。</p>
     */
    private static final Map<String, String> STATUS_OF_LABEL = Map.of(
            "仮受付", "PRELIMINARY",
            "経路提案中", "ROUTE_PROPOSED",
            "通知済み", "ROUTE_NOTIFIED",
            "確定", "CONFIRMED",
            "輸送中", "IN_TRANSIT",
            "引取済", "DELIVERED",
            "精算済", "SETTLED",
            "キャンセル", "CANCELLED");

    private String lastShipperId;
    private String lastProduct;
    private ResponseEntity<JsonMap> lastResponse;

    /**
     * いま扱っている予約の行。経路の確定（US09）のステップも同じ予約を見る。
     *
     * <p>ステップ定義をまたいで予約 ID を持ち回すと、どちらが正か分からなくなる。
     * 見つけ方は 1 か所（品名で引く）に置く。</p>
     */
    Map<String, Object> currentBooking() {
        return findByProduct(lastProduct);
    }

    /** 予約の API を叩く口。経路の確定のステップも同じ土台を使う。 */
    org.springframework.web.client.RestClient rest() {
        return rest;
    }

    /** 状態のラベル → 投影の値。ラベルの対応表を 2 か所に書かない。 */
    static String statusOf(String label) {
        String status = STATUS_OF_LABEL.get(label);
        if (status == null) {
            throw new IllegalArgumentException("知らない予約の状態です: " + label);
        }
        return status;
    }

    String url(String path) {
        return "http://localhost:" + bookingPort + path;
    }

    @かつ("メールアドレス {string} の荷主 {string} が予約用に登録されている")
    public void 荷主を用意する(String email, String name) {
        ResponseEntity<JsonMap> response = rest.post().uri(url("/api/v1/booking/shippers"))
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("name", name, "shipperType", "INDIVIDUAL", "email", email,
                        "phone", "03-0000-0000", "address", "東京都中央区",
                        "acknowledgedDuplicate", false))
                .retrieve().toEntity(JsonMap.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        lastShipperId = String.valueOf(response.getBody().get("shipperId"));
    }

    private ResponseEntity<JsonMap> book(Map<String, Object> overrides) {
        Map<String, Object> body = new LinkedHashMap<>(Map.of(
                "shipperId", lastShipperId,
                "originUnLocode", "JPTYO",
                "destinationUnLocode", "USNYC",
                "arrivalDeadline", "2026-12-01",
                "cargoType", "GENERAL",
                "weightKg", "1200",
                "lengthCm", "120",
                "widthCm", "80",
                "heightCm", "100"));
        body.put("quantity", 10);
        body.put("productName", "自動車部品");
        body.putAll(overrides);
        return rest.post().uri(url("/api/v1/booking/bookings"))
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .retrieve().toEntity(JsonMap.class);
    }

    @もし("その荷主の予約を {string} から {string} へ、到着期限 {string}、品名 {string} で登録する")
    public void 予約を登録する(String origin, String destination, String deadline, String product) {
        lastResponse = book(Map.of("originUnLocode", origin, "destinationUnLocode", destination,
                "arrivalDeadline", deadline, "productName", product));
    }

    @もし("その荷主の冷凍・冷蔵の予約を温度管理条件無しで登録する")
    public void 冷凍を温度条件なしで登録する() {
        // US05 §受入基準 2。IT2 で実装済みだが、受け入れ層では固定されていなかった。
        // 実装済みであることと、固定されていることは別。
        lastResponse = book(Map.of("cargoType", "REFRIGERATED"));
    }

    @もし("その荷主の危険物の予約を IMO クラス {string}、UN 番号 {string}、品名 {string} で登録する")
    public void 危険物を申告つきで登録する(String imoClass, String unNumber, String product) {
        lastResponse = book(Map.of("cargoType", "HAZARDOUS", "hazardImoClass", imoClass,
                "hazardUnNumber", unNumber, "productName", product));
    }

    @もし("その荷主の冷凍・冷蔵の予約を温度 {string} 〜 {string} ℃、品名 {string} で登録する")
    public void 冷凍を温度条件つきで登録する(String min, String max, String product) {
        lastResponse = book(Map.of("cargoType", "REFRIGERATED", "temperatureMinC", min,
                "temperatureMaxC", max, "productName", product));
    }

    @かつ("その予約の危険物申告は IMO クラス {string}、UN 番号 {string} である")
    public void 危険物申告を確かめる(String imoClass, String unNumber) {
        // 付帯情報は表示のためだけに運ぶ値なので、どこか一層で潰しても
        // 「登録できた」までは緑になる。一覧から読み直して確かめる。
        Map<String, Object> row = findByProduct(lastProduct);
        assertThat(row).isNotNull();
        assertThat(row.get("hazardImoClass")).isEqualTo(imoClass);
        assertThat(row.get("hazardUnNumber")).isEqualTo(unNumber);
    }

    @かつ("その予約の温度管理条件は {string} 〜 {string} ℃ である")
    public void 温度条件を確かめる(String min, String max) {
        Map<String, Object> row = findByProduct(lastProduct);
        assertThat(row).isNotNull();
        assertThat(new java.math.BigDecimal(String.valueOf(row.get("temperatureMinC"))))
                .isEqualByComparingTo(min);
        assertThat(new java.math.BigDecimal(String.valueOf(row.get("temperatureMaxC"))))
                .isEqualByComparingTo(max);
    }

    @もし("その荷主の危険物の予約を IMO クラス無しで登録する")
    public void 危険物を申告なしで登録する() {
        lastResponse = book(Map.of("cargoType", "HAZARDOUS"));
    }

    @もし("その荷主の予約を数量 {int} で登録する")
    public void 数量を指定して登録する(int quantity) {
        // 入口（@NotNull）は通り、集約の中の検査で断られる経路を選ぶ。集約が
        // 投げた例外は CommandExecutionException に包まれ、包みを解かないと
        // 500 になる。断ったのは業務の判断なので、画面には「壊れた」ではなく
        // 理由が出なければならない。
        lastResponse = book(Map.of("quantity", quantity));
    }

    @ならば("受付は成功し、予約番号が返る")
    public void 受付は成功する() {
        assertThat(lastResponse.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(lastResponse.getBody().get("bookingId")).isNotNull();
    }

    @ならば("受付は予約として断られる")
    public void 受付は断られる() {
        // 断ったことだけでなく、業務規則で断ったことまで見る。500 で落ちても
        // 「登録されなかった」ことは同じだが、画面には「壊れた」と映る。
        assertThat(lastResponse.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_CONTENT);
        assertThat(lastResponse.getBody().get("code")).isEqualTo("BUSINESS_RULE_VIOLATION");
    }

    @かつ("{int} 秒以内に予約一覧に品名 {string} の予約が現れる")
    public void 一覧に現れる(int seconds, String product) {
        SharedSteps.awaitWithin(seconds, () -> findByProduct(product) != null,
                "予約一覧に「" + product + "」が出る");
        // 次の手順が同じ予約を見られるように覚えておく。品名を固定で書くと、
        // 品名の違うシナリオを足したときに別の予約を見て緑になる。
        lastProduct = product;
    }

    @かつ("その予約の状態は {string} である")
    public void 状態を確かめる(String label) {
        Map<String, Object> row = findByProduct(lastProduct);
        assertThat(row).isNotNull();
        // **業務の言葉と API の値を対応表で突き合わせる。**
        // `assertThat(label).isEqualTo("仮受付")` は feature の文字列を自分と
        // 比べているだけで、実装を壊しても赤にならない。
        assertThat(STATUS_OF_LABEL)
                .as("feature が使う状態の呼び名は対応表に載せる")
                .containsKey(label);
        assertThat(row.get("bookingStatus")).isEqualTo(STATUS_OF_LABEL.get(label));
        assertThat(String.valueOf(row.get("bookingNumber")))
                .as("US04 §受入基準 4。UUID では人が読めない")
                .startsWith("B-");
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> findByProduct(String product) {
        ResponseEntity<JsonMap> response = rest.get()
                .uri(url("/api/v1/booking/bookings?page=0&size=200"))
                .retrieve().toEntity(JsonMap.class);
        if (response.getStatusCode() != HttpStatus.OK) {
            return null;
        }
        List<Map<String, Object>> items =
                (List<Map<String, Object>>) response.getBody().get("items");
        return items.stream()
                .filter(item -> product.equals(item.get("productName")))
                .findFirst().orElse(null);
    }

    @もし("その予約を経路設計者に引き渡す")
    public void 引き渡す() {
        Map<String, Object> row = findByProduct(lastProduct);
        assertThat(row).as("先に登録した予約が一覧に出ている").isNotNull();
        lastResponse = rest.post()
                .uri(url("/api/v1/booking/bookings/" + row.get("bookingId") + "/routing-request"))
                .retrieve().toEntity(JsonMap.class);
    }

    @もし("受け付けていない予約番号で経路設計者に引き渡す")
    public void 受け付けていない予約を引き渡す() {
        // 集約は空のまま復元される。@EventTag が抜けていると、受け付けた予約でも
        // 同じく空で復元されるので、この検査だけでは足りない（CargoTest と対にする）。
        lastResponse = rest.post()
                .uri(url("/api/v1/booking/bookings/B-NOT-EXIST/routing-request"))
                .retrieve().toEntity(JsonMap.class);
    }

    @ならば("{int} 秒以内にその予約の状態は {string} になる")
    public void 状態が変わる(int seconds, String label) {
        assertThat(STATUS_OF_LABEL).containsKey(label);
        SharedSteps.awaitWithin(seconds, () -> {
            Map<String, Object> row = findByProduct(lastProduct);
            return row != null && STATUS_OF_LABEL.get(label).equals(row.get("bookingStatus"));
        }, "予約の状態が「" + label + "」になる");
    }

    @かつ("経路設計作業一覧にその予約が出る")
    @SuppressWarnings("unchecked")
    public void 作業一覧に出る() {
        ResponseEntity<JsonMap> response = rest.get()
                .uri(url("/api/v1/booking/bookings/routing-worklist?page=0&size=200"))
                .retrieve().toEntity(JsonMap.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        List<Map<String, Object>> items =
                (List<Map<String, Object>>) response.getBody().get("items");
        assertThat(items).extracting(item -> item.get("productName")).contains(lastProduct);
    }

    @ならば("引き渡しは状態の誤りとして断られる")
    public void 引き渡しは状態の誤りで断られる() {
        // 業務規則違反（422）ではなく状態遷移違反（409）。利用者が「入力が悪い」のか
        // 「もうその段階ではない」のかを判断できるように分ける。
        assertThat(lastResponse.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(lastResponse.getBody().get("code")).isEqualTo("ILLEGAL_STATE");
    }

    /**
     * 仮受付の予約情報を修正する（US32）。
     *
     * <p>直す項目以外は<b>今の値を送り直す</b>。差し替えなので、送らなかった項目は
     * 消える。画面も同じように今の値を入れた状態で開く。</p>
     */
    private ResponseEntity<JsonMap> update(Map<String, Object> overrides) {
        Map<String, Object> row = findByProduct(lastProduct);
        assertThat(row).as("先に登録した予約が一覧に出ている").isNotNull();

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("originUnLocode", row.get("originUnLocode"));
        body.put("destinationUnLocode", row.get("destinationUnLocode"));
        body.put("arrivalDeadline", row.get("arrivalDeadline"));
        body.put("cargoType", row.get("cargoType"));
        body.put("weightKg", row.get("weightKg"));
        body.put("lengthCm", row.get("lengthCm"));
        body.put("widthCm", row.get("widthCm"));
        body.put("heightCm", row.get("heightCm"));
        body.put("quantity", row.get("quantity"));
        body.put("productName", row.get("productName"));
        body.putAll(overrides);

        return rest.put().uri(url("/api/v1/booking/bookings/" + row.get("bookingId")))
                .contentType(MediaType.APPLICATION_JSON)
                // Gateway が載せるヘッダ。誰が直したかを投影に残す。
                .header("X-Auth-Username", "sales01")
                .body(body)
                .retrieve().toEntity(JsonMap.class);
    }

    @もし("その予約の品名を {string} に直す")
    public void 品名を直す(String product) {
        lastResponse = update(Map.of("productName", product));
        if (lastResponse.getStatusCode() == HttpStatus.OK) {
            lastProduct = product;
        }
    }

    @もし("その予約を危険物に、申告無しで直す")
    public void 申告無しで危険物に直す() {
        lastResponse = update(Map.of("cargoType", "HAZARDOUS"));
    }

    @ならば("修正は成功する")
    public void 修正は成功する() {
        assertThat(lastResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @ならば("修正は状態の誤りとして断られる")
    public void 修正は状態の誤りで断られる() {
        assertThat(lastResponse.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(lastResponse.getBody().get("code")).isEqualTo("ILLEGAL_STATE");
    }

    @ならば("修正は予約として断られる")
    public void 修正は業務規則で断られる() {
        assertThat(lastResponse.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_CONTENT);
        assertThat(lastResponse.getBody().get("code")).isEqualTo("BUSINESS_RULE_VIOLATION");
    }

    @かつ("その予約には最終更新が残っている")
    public void 最終更新が残る() {
        // 誰がいつ直したかが残らないと、修正の履歴（US32 §受入基準 4）が読めない。
        Map<String, Object> row = findByProduct(lastProduct);
        assertThat(row).isNotNull();
        assertThat(row.get("updatedAt")).isNotNull();
        assertThat(row.get("updatedBy")).isEqualTo("sales01");
    }
}
