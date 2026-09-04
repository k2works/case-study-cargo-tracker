package com.example.cargotracker.booking.interfaces.rest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import com.example.cargotracker.shared.testing.AbstractAxonIntegrationTest;
import java.time.Duration;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestClient;

/**
 * 荷主登録を API から通す（US02 の縦切り）。
 *
 * <p>集約・投影の単体では「画面から見てどうなるか」を判別できない。ここで受け付けから
 * 反映までを 1 本通し、状態コードの分け方（201 / 202 / 409 / 422）を固定する。</p>
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
// クラスが終わったらコンテキストを閉じる。閉じないと複数のコンテキストが同時に
// 生きたまま同じ Axon Server にハンドラを登録し、二重登録で起動に失敗する
// （DuplicateQueryHandlerSubscriptionException）。落ちるテストが実行順で変わる。
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class ShipperControllerIT extends AbstractAxonIntegrationTest {

    @LocalServerPort
    private int port;

    // Spring Boot 4 は TestRestTemplate を廃止したので RestClient を使う。
    // 4xx / 5xx でも例外にせず状態コードで判別する（状態コードの分け方こそ検査対象）。
    private final RestClient rest = RestClient.builder()
            .defaultStatusHandler(status -> true, (request, response) -> { })
            .build();

    /** 応答本文。Map の raw 型を避けるための最小の型。 */
    static class JsonMap extends java.util.LinkedHashMap<String, Object> {
        private static final long serialVersionUID = 1L;
    }

    private String url(String path) {
        return "http://localhost:" + port + "/api/v1/booking/shippers" + path;
    }

    private ResponseEntity<JsonMap> post(String path, Map<String, Object> body) {
        return rest.post().uri(url(path))
                .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                .body(body).retrieve().toEntity(JsonMap.class);
    }

    private ResponseEntity<JsonMap> get(String path) {
        return rest.get().uri(url(path)).retrieve().toEntity(JsonMap.class);
    }

    private Map<String, Object> corporate(String email) {
        return Map.of("name", "山田商事", "shipperType", "CORPORATE", "email", email,
                "phone", "03-1111-1111", "address", "東京都中央区",
                "contractNumber", "CT-0001", "discountRate", "0.1000");
    }

    @Test
    @DisplayName("登録は 201 で識別子を返し、やがて詳細が 200 で読める")
    void registersAndBecomesReadable() {
        String email = "it-" + System.nanoTime() + "@example.com";

        ResponseEntity<JsonMap> created = post("", corporate(email));

        assertThat(created.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        String shipperId = (String) created.getBody().get("shipperId");
        assertThat(shipperId).isNotBlank();

        // 投影は非同期。「受け付けた」と「反映した」は別なので待って確かめる。
        await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> {
            ResponseEntity<JsonMap> found = get("/" + shipperId);
            assertThat(found.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(found.getBody().get("name")).isEqualTo("山田商事");
            assertThat(found.getBody().get("shipperCode").toString()).matches("SHP-\\d{6}");
        });
    }

    @Test
    @DisplayName("反映前の詳細は 404 でなく 202 を返す")
    void returnsAcceptedWhileProjectionIsBehind() {
        ResponseEntity<JsonMap> response = get("/unknown-" + System.nanoTime());

        assertThat(response.getStatusCode())
                .as("404 だと「登録に失敗した」と読めてしまう。受付と反映は別")
                .isEqualTo(HttpStatus.ACCEPTED);
        assertThat(response.getBody().get("message").toString()).contains("反映");
    }

    @Test
    @DisplayName("同じメールアドレスは 409 で断る")
    void rejectsDuplicateEmail() {
        String email = "dup-" + System.nanoTime() + "@example.com";
        post("", corporate(email));

        await().atMost(Duration.ofSeconds(10)).untilAsserted(() ->
                assertThat(post("", corporate(email)).getStatusCode()).isEqualTo(HttpStatus.CONFLICT));
    }

    @Test
    @DisplayName("割引率が範囲外なら 422 で断る")
    void rejectsDiscountRateOutOfRange() {
        Map<String, Object> body = Map.of("name", "過大割引", "shipperType", "CORPORATE",
                "email", "over-" + System.nanoTime() + "@example.com",
                "contractNumber", "CT-9", "discountRate", "0.9");

        ResponseEntity<JsonMap> response = post("", body);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_CONTENT);
        assertThat(response.getBody().get("code")).isEqualTo("BUSINESS_RULE_VIOLATION");
    }

    @Test
    @DisplayName("個人の荷主も REST から登録でき、一覧に出る")
    void registersIndividual() {
        // ドメイン単体とフロント単体はあったが、REST と受け入れは CORPORATE 固定
        // だった（IT1 レビュー M8）。JSON → enum の配線ミスは下 2 層だけでは
        // 判別しない。
        String email = "ind-" + System.nanoTime() + "@example.com";
        Map<String, Object> body = Map.of("name", "山田 太郎", "shipperType", "INDIVIDUAL",
                "email", email, "phone", "03-1111-1111", "address", "東京都中央区");

        assertThat(post("", body).getStatusCode()).isEqualTo(HttpStatus.CREATED);

        await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> {
            ResponseEntity<JsonMap> list = get("?page=0&size=200");
            assertThat(list.getBody().get("items").toString())
                    .contains(email)
                    .contains("INDIVIDUAL");
        });
    }

    @Test
    @DisplayName("個人に法人契約は付けられない")
    void rejectsIndividualWithContract() {
        Map<String, Object> body = Map.of("name", "山田 太郎", "shipperType", "INDIVIDUAL",
                "email", "ind-ng-" + System.nanoTime() + "@example.com",
                "contractNumber", "CT-1", "discountRate", "0.1000");

        ResponseEntity<JsonMap> response = post("", body);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_CONTENT);
        assertThat(response.getBody().get("code")).isEqualTo("BUSINESS_RULE_VIOLATION");
    }

    @Test
    @DisplayName("割引率の境目は 30% ちょうどまで通る")
    void acceptsDiscountRateAtUpperBound() {
        // 0.9 だけを試すと、境目が 0.3 なのか 0.5 なのか分からない。US03 は
        // 「0〜30% の範囲で設定できる」なので、通る側の端も固定する。
        Map<String, Object> ok = Map.of("name", "満額割引", "shipperType", "CORPORATE",
                "email", "max-" + System.nanoTime() + "@example.com",
                "contractNumber", "CT-30", "discountRate", "0.3000");
        Map<String, Object> ng = Map.of("name", "超過割引", "shipperType", "CORPORATE",
                "email", "over1-" + System.nanoTime() + "@example.com",
                "contractNumber", "CT-31", "discountRate", "0.3001");

        assertThat(post("", ok).getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(post("", ng).getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_CONTENT);
    }

    @Test
    @DisplayName("要確認一覧はロールが伝わらなければ何も出さない")
    void attentionItemsNeedRoles() {
        // 既定で営業宛を出すと、伝達が壊れていることに気づかないまま他ロールの
        // 担当分が見える。
        String base = "http://localhost:" + port + "/api/v1/booking/attention-items";
        assertThat(rest.get().uri(base).retrieve().toEntity(JsonMap.class)
                .getBody().get("items").toString()).isEqualTo("[]");
        assertThat(rest.get().uri(base).header("X-Auth-Roles", "  ")
                .retrieve().toEntity(JsonMap.class).getBody().get("items").toString())
                .isEqualTo("[]");
        assertThat(rest.get().uri(base).header("X-Auth-Roles", "ROLE_HANDLER")
                .retrieve().toEntity(JsonMap.class).getBody().get("items").toString())
                .as("担当外のロールには自分宛が無い")
                .isEqualTo("[]");
    }

    @Test
    @DisplayName("重複で弾いた行から既存の荷主を辿れる")
    void attentionItemPointsToExistingShipper() {
        String email = "dup-" + System.nanoTime() + "@example.com";
        post("", corporate(email));
        await().atMost(Duration.ofSeconds(10)).untilAsserted(() ->
                assertThat(get("?page=0&size=200").getBody().get("items").toString())
                        .contains(email));

        Map<String, Object> again = new java.util.HashMap<>(corporate(email));
        again.put("acknowledgedDuplicate", true);
        post("", again);

        await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> {
            String body = rest.get()
                    .uri("http://localhost:" + port + "/api/v1/booking/attention-items")
                    .header("X-Auth-Roles", "ROLE_SALES")
                    .retrieve().toEntity(JsonMap.class).getBody().get("items").toString();
            assertThat(body).contains("メールアドレスの重複");
            // 応答に payload（個人情報）は載せない。載せるのは識別子だけ（ADR-0003 決定 6）。
            assertThat(body).doesNotContain(email);
            assertThat(body).contains("relatedShipperId=");
        });
    }

    @Test
    @DisplayName("一覧に登録した荷主が出る")
    void listsShippers() {
        String email = "list-" + System.nanoTime() + "@example.com";
        post("", corporate(email));

        await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> {
            ResponseEntity<JsonMap> list = get("?page=0&size=200");
            assertThat(list.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(list.getBody().get("items").toString()).contains(email);
        });
    }
}
