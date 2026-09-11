package com.example.cargotracker.billing.interfaces.rest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import com.example.cargotracker.billing.application.reaction.BillingReactionHandler;
import com.example.cargotracker.billing.infrastructure.projection.BillingCargoProjection;
import com.example.cargotracker.billing.infrastructure.projection.ShipperContractProjection;
import com.example.cargotracker.shared.contract.event.CargoDeliveredEvent;
import com.example.cargotracker.shared.contract.event.ShipperRegisteredEvent;
import com.example.cargotracker.shared.contract.event.TrackingInitializedEvent;
import com.example.cargotracker.shared.testing.AbstractAxonIntegrationTest;
import java.math.BigDecimal;
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
 * 請求の API（S60・S61 が読む）。
 *
 * <p><b>画面から踏む形で確かめる。</b> 層ごとの検査は自分の層しか見ない——
 * クエリが返すものと、画面が受け取るものは別である（IT11 の教訓）。</p>
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class InvoiceControllerIT extends AbstractAxonIntegrationTest {

    private static final Instant AT = Instant.parse("2026-09-28T01:00:00Z");

    @LocalServerPort
    private int port;

    @Autowired
    private BillingReactionHandler reactions;

    @Autowired
    private BillingCargoProjection cargoProjection;

    @Autowired
    private ShipperContractProjection shipperProjection;

    private final RestClient rest = RestClient.builder()
            .defaultStatusHandler(status -> true, (request, response) -> { })
            .build();

    private String url(String path) {
        return "http://localhost:" + port + "/api/v1/billing/invoices" + path;
    }

    /** JSON をそのまま受ける型。画面が受け取る形で読む。 */
    private static final org.springframework.core.ParameterizedTypeReference<Map<String, Object>>
            JSON = new org.springframework.core.ParameterizedTypeReference<>() { };

    private Map<String, Object> invoiceOf(String bookingId) {
        ResponseEntity<Map<String, Object>> response = rest.get()
                .uri(url("/by-booking/" + bookingId)).retrieve().toEntity(JSON);
        return response.getStatusCode().is2xxSuccessful() ? response.getBody() : null;
    }

    /** 引取まで通った予約を 1 件作る。 */
    private String deliveredBooking(String shipperType, String discountRate) {
        String suffix = String.valueOf(System.nanoTime());
        String trackingNumber = "TRK-CI" + suffix.substring(suffix.length() - 9);
        String bookingId = "B-CI-" + suffix;
        String shipperId = "SHP-CI-" + suffix;

        shipperProjection.on(new ShipperRegisteredEvent(shipperId, shipperType, "山田商事",
                shipperId + "@example.com", "03-0000-0000", "東京都港区",
                discountRate == null ? null : "CT-0012", discountRate));
        cargoProjection.on(new TrackingInitializedEvent(trackingNumber, bookingId, shipperId,
                "JPTYO", "USNYC", "GENERAL", new BigDecimal("1200"),
                List.of(new TrackingInitializedEvent.Leg("V-MOL-001", "JPTYO", "SGSIN",
                                AT, AT.plusSeconds(86_400)),
                        new TrackingInitializedEvent.Leg("V-ONE-002", "SGSIN", "USNYC",
                                AT.plusSeconds(90_000), AT.plusSeconds(600_000))),
                AT), "evt-" + System.nanoTime());
        reactions.on(new CargoDeliveredEvent(trackingNumber, bookingId, AT, "USNYC"));

        await().atMost(Duration.ofSeconds(30)).until(() -> invoiceOf(bookingId) != null);
        return bookingId;
    }

    @Test
    @DisplayName("S61: 請求書の詳細が根拠つきで読める")
    @SuppressWarnings("unchecked")
    void readsTheInvoiceWithItsBasis() {
        String bookingId = deliveredBooking("CORPORATE", "0.1500");

        Map<String, Object> invoice = invoiceOf(bookingId);
        assertThat(invoice.get("statusLabel")).isEqualTo("算出済");
        assertThat(new BigDecimal(String.valueOf(invoice.get("totalAmount"))))
                .isEqualByComparingTo("433500");
        List<Map<String, Object>> lines = (List<Map<String, Object>>) invoice.get("lineItems");
        assertThat(lines).extracting(line -> line.get("itemTypeLabel"))
                .containsExactly("基本料金", "割引", "消費税");
        assertThat(String.valueOf(lines.getFirst().get("description")))
                .as("画面は根拠をそのまま出す")
                .contains("2 区間").contains("1,200 kg");
    }

    @Test
    @DisplayName("S60: 一覧に出て、予約で絞れる")
    @SuppressWarnings("unchecked")
    void listsInvoices() {
        String bookingId = deliveredBooking("INDIVIDUAL", null);

        ResponseEntity<Map<String, Object>> response = rest.get()
                .uri(url("?bookingId=" + bookingId)).retrieve().toEntity(JSON);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        List<Map<String, Object>> items =
                (List<Map<String, Object>>) response.getBody().get("items");
        assertThat(items).singleElement()
                .satisfies(item -> {
                    assertThat(item.get("bookingId")).isEqualTo(bookingId);
                    assertThat(item.get("shipperTypeLabel")).isEqualTo("個人");
                });
    }

    @Test
    @DisplayName("S61: 調整を入れると合計が動き、根拠の例外が明細に残る")
    @SuppressWarnings("unchecked")
    void adjustsTheInvoice() {
        String bookingId = deliveredBooking("CORPORATE", "0.1500");
        String invoiceId = String.valueOf(invoiceOf(bookingId).get("invoiceId"));

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("amount", "-10000");
        body.put("reason", "誤配による再設計");
        body.put("basisExceptionId", "EX-2026-0928-03");
        ResponseEntity<Void> response = rest.post().uri(url("/" + invoiceId + "/adjustments"))
                .header("X-Auth-Username", "accountant01")
                .contentType(MediaType.APPLICATION_JSON)
                .body(body).retrieve().toBodilessEntity();
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);

        await().atMost(Duration.ofSeconds(30)).untilAsserted(() -> {
            Map<String, Object> invoice = invoiceOf(bookingId);
            assertThat(new BigDecimal(String.valueOf(invoice.get("totalAmount"))))
                    .isEqualByComparingTo("423500");
            List<Map<String, Object>> lines =
                    (List<Map<String, Object>>) invoice.get("lineItems");
            assertThat(lines)
                    .filteredOn(line -> "ADJUSTMENT".equals(line.get("itemType")))
                    .singleElement()
                    .satisfies(line -> assertThat(line.get("basisExceptionId"))
                            .as("根拠の例外へ飛べないと、あとから確かめられない")
                            .isEqualTo("EX-2026-0928-03"));
        });
    }

    @Test
    @DisplayName("理由の無い調整は入力の誤りとして断られる")
    void refusesAdjustmentWithoutReason() {
        String bookingId = deliveredBooking("INDIVIDUAL", null);
        String invoiceId = String.valueOf(invoiceOf(bookingId).get("invoiceId"));

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("amount", "-10000");
        body.put("reason", "  ");
        ResponseEntity<Void> response = rest.post().uri(url("/" + invoiceId + "/adjustments"))
                .header("X-Auth-Username", "accountant01")
                .contentType(MediaType.APPLICATION_JSON)
                .body(body).retrieve().toBodilessEntity();

        // 入力の誤りは 422（architecture_backend.md「例外と HTTP の対応」）。
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_CONTENT);
    }

    @Test
    @DisplayName("無い請求書は 404（「まだ算出されていない」も開く先が無い）")
    void returnsNotFoundForUnknownInvoice() {
        assertThat(rest.get().uri(url("/INV-NOPE-00000000")).retrieve().toBodilessEntity()
                .getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(rest.get().uri(url("/by-booking/B-NOPE")).retrieve().toBodilessEntity()
                .getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }
}
