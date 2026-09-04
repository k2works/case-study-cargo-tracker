package com.example.cargotracker.booking.interfaces.rest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import com.example.cargotracker.shared.testing.AbstractAxonIntegrationTest;
import java.time.Duration;
import java.util.LinkedHashMap;
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
