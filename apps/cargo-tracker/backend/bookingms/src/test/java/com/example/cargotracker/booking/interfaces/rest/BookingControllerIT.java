package com.example.cargotracker.booking.interfaces.rest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import com.example.cargotracker.shared.testing.AbstractAxonIntegrationTest;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.web.client.RestClient;

/**
 * 貨物予約を API から通す（US04 の縦切り）。
 *
 * <p>状態コードの分け方（201 / 202 / 409 / 422）を固定する。とくに<b>集約が断ったとき</b>が
 * 500 にならないこと。集約の例外は {@code CommandExecutionException} に包まれて届くので、
 * 包みを解かないと「壊れた」と表示される。</p>
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class BookingControllerIT extends AbstractAxonIntegrationTest {

    static class JsonMap extends LinkedHashMap<String, Object> {
        private static final long serialVersionUID = 1L;
    }

    @LocalServerPort
    private int port;

    private final RestClient rest = RestClient.builder()
            .defaultStatusHandler(status -> true, (request, response) -> { })
            .build();

    private String url(String path) {
        return "http://localhost:" + port + "/api/v1/booking/bookings" + path;
    }

    private ResponseEntity<JsonMap> post(Map<String, Object> body) {
        return rest.post().uri(url("")).contentType(MediaType.APPLICATION_JSON)
                .body(body).retrieve().toEntity(JsonMap.class);
    }

    /** Gateway が付ける利用者名を添える。実際の経路では必ず付く。 */
    private ResponseEntity<JsonMap> postTo(String path, Map<String, Object> body) {
        return rest.post().uri(url(path)).contentType(MediaType.APPLICATION_JSON)
                .header("X-Auth-Username", "sales01")
                .body(body).retrieve().toEntity(JsonMap.class);
    }

    private ResponseEntity<JsonMap> put(String path, Map<String, Object> body) {
        return rest.put().uri(url(path)).contentType(MediaType.APPLICATION_JSON)
                .body(body).retrieve().toEntity(JsonMap.class);
    }

    private ResponseEntity<JsonMap> get(String path) {
        return rest.get().uri(url(path)).retrieve().toEntity(JsonMap.class);
    }

    private static Map<String, Object> request(Map<String, Object> overrides) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("shipperId", "SHP-IT-" + System.nanoTime());
        body.put("originUnLocode", "JPTYO");
        body.put("destinationUnLocode", "USNYC");
        body.put("arrivalDeadline", "2026-12-01");
        body.put("cargoType", "GENERAL");
        body.put("weightKg", "1200");
        body.put("lengthCm", "120");
        body.put("widthCm", "80");
        body.put("heightCm", "100");
        body.put("quantity", 10);
        body.put("productName", "自動車部品");
        body.putAll(overrides);
        return body;
    }

    @Test
    @DisplayName("受け付けて 201 を返し、数秒後に一覧と詳細へ出る")
    void booksAndBecomesReadable() {
        String product = "部品-" + System.nanoTime();

        ResponseEntity<JsonMap> created = post(request(Map.of("productName", product)));

        assertThat(created.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        String bookingId = String.valueOf(created.getBody().get("bookingId"));

        await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> {
            ResponseEntity<JsonMap> detail = get("/" + bookingId);
            assertThat(detail.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(detail.getBody().get("bookingStatus")).isEqualTo("PRELIMINARY");
        });

        assertThat(get("?page=0&size=200").getBody().get("items").toString()).contains(product);
    }

    @Test
    @DisplayName("US10: 引き渡した予約の条件を調整でき、期限が変わる")
    void adjustsRouteSpecification() {
        String bookingId = handedOverBooking();

        ResponseEntity<JsonMap> adjusted = put("/" + bookingId + "/route-specification",
                Map.of("arrivalDeadline", "2027-01-31",
                        "excludeUnLocodes", List.of("SGSIN"),
                        "departFromUnLocode", "JPOSA"));

        assertThat(adjusted.getStatusCode()).isEqualTo(HttpStatus.OK);
        await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> {
            JsonMap detail = get("/" + bookingId).getBody();
            assertThat(detail.get("arrivalDeadline")).isEqualTo("2027-01-31");
            // 条件が変われば、確定済みの経路はその条件で組んだものではなくなる。
            assertThat(detail.get("routingStatus")).isEqualTo("ROUTING_REQUESTED");
        });
    }

    @Test
    @DisplayName("US10: 出発地・目的地を除外しようとすると 422 で断る（画面が 500 にならない）")
    void rejectsExcludingTheEndpoints() {
        String bookingId = handedOverBooking();

        ResponseEntity<JsonMap> response = put("/" + bookingId + "/route-specification",
                Map.of("arrivalDeadline", "2027-01-31",
                        "excludeUnLocodes", List.of("USNYC")));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_CONTENT);
        assertThat(response.getBody().get("code")).isEqualTo("BUSINESS_RULE_VIOLATION");
    }

    @Test
    @DisplayName("US10 §4: 差し戻すと営業の受け皿に出る（状態は動かない）")
    void requestsConditionReview() {
        String bookingId = handedOverBooking();

        ResponseEntity<JsonMap> response = postTo("/" + bookingId + "/condition-review",
                Map.of("reason", "期限内に着ける便がありません"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> {
            assertThat(get("/" + bookingId).getBody().get("routingStatus"))
                    .as("差し戻しは状態遷移にしない（ADR-0009 決定 1）")
                    .isEqualTo("ROUTING_REQUESTED");
            assertThat(rest.get().uri(url("/condition-reviews")).retrieve()
                    .toEntity(JsonMap.class).getBody().get("items").toString())
                    .contains(bookingId);
        });
    }

    @Test
    @DisplayName("US10 §4: 理由の無い差し戻しは 422 で断る")
    void rejectsConditionReviewWithoutReason() {
        String bookingId = handedOverBooking();

        ResponseEntity<JsonMap> response =
                postTo("/" + bookingId + "/condition-review", Map.of("reason", "  "));

        // 入力の検証は 422 で返す（このサービスの既定。ApiExceptionHandler）。
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_CONTENT);
    }

    @Test
    @DisplayName("引き渡していない予約の条件は調整できない（409 で断り、画面が 500 にならない）")
    void rejectsAdjustmentBeforeRoutingRequested() {
        ResponseEntity<JsonMap> created = post(request(Map.of()));
        String bookingId = String.valueOf(created.getBody().get("bookingId"));

        ResponseEntity<JsonMap> response = put("/" + bookingId + "/route-specification",
                Map.of("arrivalDeadline", "2027-01-31"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    @DisplayName("US12: 経路が決まった予約を通知でき、履歴に残る")
    void notifiesShipper() {
        String bookingId = routedBooking();

        ResponseEntity<JsonMap> response = postTo("/" + bookingId + "/notifications",
                Map.of("recipientEmail", "shipper@example.com",
                        "summary", "JPTYO → USNYC / 14 日 / 2026-09-24 着"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> {
            assertThat(get("/" + bookingId).getBody().get("bookingStatus"))
                    .isEqualTo("ROUTE_NOTIFIED");
            assertThat(get("/" + bookingId + "/notifications").getBody().get("items").toString())
                    .contains("shipper@example.com")
                    .contains("2026-09-24 着");
        });
    }

    @Test
    @DisplayName("US12: 経路が決まっていない予約の通知は 409 で断る（画面が 500 にならない）")
    void rejectsNotificationBeforeRouting() {
        String bookingId = handedOverBooking();

        ResponseEntity<JsonMap> response = postTo("/" + bookingId + "/notifications",
                Map.of("recipientEmail", "shipper@example.com", "summary", "経路"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    @DisplayName("US12: 利用者名の無い通知でも 500 にしない（記録は残す）")
    void notifiesWithoutActor() {
        // Gateway を通れば X-Auth-Username は必ず入るが、入らなかったときに
        // 落とすのは違う。通知した事実は残し、「誰が」は画面で「—」と出す。
        String bookingId = routedBooking();

        ResponseEntity<JsonMap> response = rest.post()
                .uri(url("/" + bookingId + "/notifications"))
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("recipientEmail", "shipper@example.com", "summary", "経路"))
                .retrieve().toEntity(JsonMap.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        await().atMost(Duration.ofSeconds(10)).untilAsserted(() ->
                assertThat(get("/" + bookingId + "/notifications").getBody()
                        .get("items").toString()).contains("shipper@example.com"));
    }

    @Test
    @DisplayName("US12 §4: 一度も通知していない予約の履歴は空（404 にしない）")
    void emptyNotificationHistory() {
        ResponseEntity<JsonMap> response = get("/" + handedOverBooking() + "/notifications");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().get("items").toString()).isEqualTo("[]");
    }

    @Test
    @DisplayName("US12: 通知した予約を経路設計へ戻すと作業一覧に戻る")
    void returnsToRouting() {
        String bookingId = routedBooking();
        assertThat(postTo("/" + bookingId + "/notifications",
                Map.of("recipientEmail", "shipper@example.com", "summary", "経路"))
                .getStatusCode()).isEqualTo(HttpStatus.OK);
        await().atMost(Duration.ofSeconds(10)).untilAsserted(() ->
                assertThat(get("/" + bookingId).getBody().get("bookingStatus"))
                        .isEqualTo("ROUTE_NOTIFIED"));

        ResponseEntity<JsonMap> response = postTo("/" + bookingId + "/return-to-routing",
                Map.of("reason", "荷主が経由港の変更を希望"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> {
            JsonMap detail = get("/" + bookingId).getBody();
            assertThat(detail.get("bookingStatus")).isEqualTo("ROUTE_PROPOSED");
            assertThat(detail.get("routingStatus")).isEqualTo("ROUTING_REQUESTED");
        });
        // 戻しても「何を伝えたか」は残る。
        assertThat(get("/" + bookingId + "/notifications").getBody().get("items").toString())
                .contains("shipper@example.com");
    }

    @Test
    @DisplayName("US12: 通知していない予約は経路設計へ戻せない（409）")
    void rejectsReturnBeforeNotification() {
        ResponseEntity<JsonMap> response = postTo(
                "/" + routedBooking() + "/return-to-routing", Map.of("reason", "変更希望"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    }

    /** 引き渡して経路まで確定した予約。 */
    private String routedBooking() {
        String bookingId = handedOverBooking();
        assertThat(postTo("/" + bookingId + "/route", Map.of("legs", List.of(Map.of(
                "voyageNumber", "V-IT-001",
                "loadUnLocode", "JPTYO",
                "unloadUnLocode", "USNYC",
                "loadTime", "2026-09-10T00:00:00Z",
                "unloadTime", "2026-11-20T00:00:00Z")))).getStatusCode())
                .isEqualTo(HttpStatus.OK);
        await().atMost(Duration.ofSeconds(10)).untilAsserted(() ->
                assertThat(get("/" + bookingId).getBody().get("routingStatus"))
                        .isEqualTo("ROUTED"));
        return bookingId;
    }

    /** 受け付けて経路設計へ引き渡した予約。投影が追いつくまで待つ。 */
    @Test
    @DisplayName("US13: 通知していない予約は API を直接叩いても確定できない")
    void rejectsConfirmationBeforeNotification() {
        // **集約の検査だけでは、画面での見え方（500 か 409 か）を判別しない。**
        // 画面はボタンを出さないが、ボタンの出し分けは守りではない。
        String bookingId = handedOverBooking();

        ResponseEntity<JsonMap> response = postTo("/" + bookingId + "/confirmation", Map.of());

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    @DisplayName("断った理由が利用者に届く（器だけの文言に化けない）")
    void refusalCarriesTheReason() {
        // **ステータスコードだけを見る検査では判別できなかった**（IT7 のクラスタで実測）。
        // Axon Server 越しに来ると「An exception was thrown by the remote message
        // handling component: 」という器だけの文言が最深に来る。印の付いた文言を
        // 優先しないと、断った理由が画面に出ない。
        String bookingId = handedOverBooking();

        ResponseEntity<JsonMap> response = postTo("/" + bookingId + "/confirmation", Map.of());

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(String.valueOf(response.getBody().get("message")))
                .as("なぜ断られたのかが読めないと、利用者は次に何をすればよいか分からない")
                .contains("確定できません")
                .doesNotContain("remote message handling component")
                // 例外クラスの完全名を業務担当者に見せない。
                .doesNotContain("com.example.cargotracker");
    }

    private String handedOverBooking() {
        ResponseEntity<JsonMap> created = post(request(Map.of()));
        String bookingId = String.valueOf(created.getBody().get("bookingId"));
        assertThat(postTo("/" + bookingId + "/routing-request", Map.of()).getStatusCode())
                .isEqualTo(HttpStatus.ACCEPTED);
        await().atMost(Duration.ofSeconds(10)).untilAsserted(() ->
                assertThat(get("/" + bookingId).getBody().get("routingStatus"))
                        .isEqualTo("ROUTING_REQUESTED"));
        return bookingId;
    }

    @Test
    @DisplayName("S34: 経路を組んでいない航海の影響範囲は空の一覧を返す（404 にしない）")
    void returnsEmptyAffectedBookingsForUnusedVoyage() {
        // 404 にすると「そんな航海は無い」と読める。止めてよいかを確かめに来た人が、
        // 航海番号を打ち間違えたのだと思って探し直すことになる。
        //
        // **画面から呼ぶ経路を HTTP の層で 1 本通す。** クエリだけを見ていると、
        // /{bookingId} に吸われて予約 ID として解決される（202）ことに気づけない。
        ResponseEntity<JsonMap> response =
                get("/by-voyage/V-NONE-" + System.nanoTime());

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().get("items").toString()).isEqualTo("[]");
    }

    @Test
    @DisplayName("投影がまだなら 404 ではなく 202 を返す")
    void returnsAcceptedWhileProjectionIsBehind() {
        // 404 にすると「登録に失敗した」と読めてしまう。
        ResponseEntity<JsonMap> response = get("/not-projected-yet");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
        assertThat(response.getBody().get("message").toString()).contains("反映");
    }

    @Test
    @DisplayName("出発地と目的地が同じなら 422 で断る")
    void rejectsSameOriginAndDestination() {
        ResponseEntity<JsonMap> response = post(request(Map.of("destinationUnLocode", "JPTYO")));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_CONTENT);
        assertThat(response.getBody().get("code")).isEqualTo("BUSINESS_RULE_VIOLATION");
    }

    @Test
    @DisplayName("危険物の申告漏れは 422 で断る")
    void rejectsHazardousWithoutDeclaration() {
        ResponseEntity<JsonMap> response = post(request(Map.of("cargoType", "HAZARDOUS")));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_CONTENT);
        assertThat(response.getBody().get("message").toString()).contains("危険物申告");
    }

    @Test
    @DisplayName("冷凍の温度条件漏れは 422 で断る")
    void rejectsRefrigeratedWithoutTemperature() {
        ResponseEntity<JsonMap> response = post(request(Map.of("cargoType", "REFRIGERATED")));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_CONTENT);
        assertThat(response.getBody().get("message").toString()).contains("温度管理条件");
    }

    @Test
    @DisplayName("危険物と冷凍を申告つきで受け付ける")
    void acceptsHazardousAndRefrigerated() {
        assertThat(post(request(Map.of("cargoType", "HAZARDOUS",
                "hazardImoClass", "3", "hazardUnNumber", "UN1263"))).getStatusCode())
                .isEqualTo(HttpStatus.CREATED);
        assertThat(post(request(Map.of("cargoType", "REFRIGERATED",
                "temperatureMinC", "-20", "temperatureMaxC", "-10"))).getStatusCode())
                .isEqualTo(HttpStatus.CREATED);
    }

    @Test
    @DisplayName("集約が断ったときも 500 にしない")
    void mapsAggregateRejectionToBusinessRuleViolation() {
        // 数量は入口（@NotNull）を通り、集約の中の検査で断られる。集約の例外は
        // CommandExecutionException に包まれて届くので、包みを解かないと 500 になる。
        ResponseEntity<JsonMap> response = post(request(Map.of("quantity", 0)));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_CONTENT);
        assertThat(response.getBody().get("code")).isEqualTo("BUSINESS_RULE_VIOLATION");
    }

    @Test
    @DisplayName("形が足りない要求は入口で断る")
    void rejectsMalformedRequest() {
        ResponseEntity<JsonMap> response = post(request(Map.of("productName", " ")));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_CONTENT);
        assertThat(response.getBody().get("code")).isEqualTo("INVALID_REQUEST");
    }

    @Test
    @DisplayName("仮受付の件数を返す")
    void returnsSummary() {
        post(request(Map.of()));

        ResponseEntity<JsonMap> response = get("/summary");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(((Number) response.getBody().get("preliminary")).intValue()).isPositive();
    }
}
