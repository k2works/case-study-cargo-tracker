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

    private String lastShipperId;
    private ResponseEntity<JsonMap> lastResponse;

    private String url(String path) {
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
    }

    @かつ("その予約の状態は {string} である")
    public void 状態を確かめる(String label) {
        Map<String, Object> row = findByProduct("自動車部品");
        assertThat(row).isNotNull();
        assertThat(row.get("bookingStatus")).isEqualTo("PRELIMINARY");
        assertThat(label).isEqualTo("仮受付");
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
}
