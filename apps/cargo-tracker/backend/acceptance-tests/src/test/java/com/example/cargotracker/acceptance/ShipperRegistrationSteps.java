package com.example.cargotracker.acceptance;

import static org.assertj.core.api.Assertions.assertThat;

import io.cucumber.java.ja.かつ;
import io.cucumber.java.ja.ならば;
import io.cucumber.java.ja.もし;
import io.cucumber.java.ja.前提;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestClient;

/**
 * デモ項目 3・4 のステップ。
 *
 * <p>bookingms を実際に起動し、API を叩いて確かめる。集約や投影を直接呼ぶと、
 * 「画面から見てどうなるか」を判別できない。</p>
 */
public class ShipperRegistrationSteps {

    static class JsonMap extends LinkedHashMap<String, Object> {
        private static final long serialVersionUID = 1L;
    }

    @LocalServerPort
    private int bookingPort;

    private final RestClient rest = RestClient.builder()
            .defaultStatusHandler(status -> true, (request, response) -> { })
            .build();

    private ResponseEntity<JsonMap> lastResponse;

    private String url(String path) {
        return "http://localhost:" + bookingPort + path;
    }

    private ResponseEntity<JsonMap> get(String path) {
        return rest.get().uri(url(path)).retrieve().toEntity(JsonMap.class);
    }

    /**
     * 営業として読む。ロールは本来 Gateway が JWT から取り出して伝える。
     * この受け入れテストは bookingms を直接叩くので、同じヘッダを自分で付ける。
     */
    private ResponseEntity<JsonMap> getAsSales(String path) {
        return rest.get().uri(url(path))
                .header("X-Auth-Roles", "ROLE_SALES")
                .retrieve().toEntity(JsonMap.class);
    }

    private ResponseEntity<JsonMap> registerCorporate(String name, String email) {
        return registerCorporate(name, email, false);
    }

    private ResponseEntity<JsonMap> registerCorporate(String name, String email,
            boolean acknowledgedDuplicate) {
        return rest.post().uri(url("/api/v1/booking/shippers"))
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("name", name, "shipperType", "CORPORATE", "email", email,
                        "phone", "03-0000-0000", "address", "東京都中央区",
                        "contractNumber", "CT-0001", "discountRate", "0.1000",
                        "acknowledgedDuplicate", acknowledgedDuplicate))
                .retrieve().toEntity(JsonMap.class);
    }

    @前提("営業担当者 {string} でログインしている")
    public void ログインしている(String username) {
        // 認可は Gateway が担う。ここでは営業として操作することだけを表す。
        assertThat(username).isNotBlank();
    }

    @かつ("メールアドレス {string} の荷主 {string} が登録されている")
    public void 荷主が登録されている(String email, String name) {
        assertThat(registerCorporate(name, email).getStatusCode()).isEqualTo(HttpStatus.CREATED);
        SharedSteps.awaitWithin(10, () -> listContains(name), "先に登録した荷主が一覧に出る");
    }

    @もし("メールアドレス {string} で法人の荷主 {string} を登録する")
    public void 荷主を登録する(String email, String name) {
        lastResponse = registerCorporate(name, email);
    }

    @もし("同じメールアドレス {string} で荷主 {string} を登録する")
    public void 同じメールアドレスで登録する(String email, String name) {
        // 重複の問いかけに「続ける」と答えた状態。1 段目を越えた先で、
        // 2 段目（投影の UNIQUE）と 3 段目（要確認一覧）が働くことを見る。
        lastResponse = registerCorporate(name, email, true);
    }

    @ならば("受付は成功する")
    public void 受付は成功する() {
        assertThat(lastResponse.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(lastResponse.getBody().get("shipperId")).isNotNull();
    }

    @かつ("{int} 秒以内に荷主一覧に {string} が現れる")
    public void 一覧に現れる(int seconds, String name) {
        SharedSteps.awaitWithin(seconds, () -> listContains(name), name + " が一覧に出る");
    }

    @ならば("{int} 秒以内に要確認一覧に {string} が {int} 件現れる")
    public void 要確認一覧に現れる(int seconds, String reason, int count) {
        SharedSteps.awaitWithin(seconds, () -> countAttention(reason) >= count,
                "要確認一覧に「" + reason + "」が出る");
    }

    @かつ("その要確認の担当ロールは {string} である")
    public void 担当ロール(String role) {
        assertThat(attentionItems()).anySatisfy(item ->
                assertThat(item.get("assignedRole")).isEqualTo(role));
    }

    private boolean listContains(String name) {
        ResponseEntity<JsonMap> response = get("/api/v1/booking/shippers?page=0&size=200");
        return response.getStatusCode().is2xxSuccessful()
                && String.valueOf(response.getBody().get("items")).contains(name);
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> attentionItems() {
        ResponseEntity<JsonMap> response = getAsSales("/api/v1/booking/attention-items");
        Object items = response.getBody() == null ? null : response.getBody().get("items");
        return items instanceof List ? (List<Map<String, Object>>) items : List.of();
    }

    private long countAttention(String reason) {
        return attentionItems().stream()
                .filter(item -> reason.equals(item.get("reason")))
                .count();
    }
}
