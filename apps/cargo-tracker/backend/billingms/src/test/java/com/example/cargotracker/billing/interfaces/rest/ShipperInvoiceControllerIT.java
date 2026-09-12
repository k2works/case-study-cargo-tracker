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
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.web.client.RestClient;

/**
 * 荷主が読む自社の請求書の API（S62 / US23 §受入基準 2・デモ項目 D8）。
 *
 * <p><b>画面から踏む形で確かめる。</b> クエリ側の検査（{@code ShipperInvoiceQueryIT}）は
 * 絞りそのものを見るが、<b>荷主 ID が Gateway からどう届くか</b>は HTTP の層にしか
 * 無い——ヘッダを読み忘れても、クエリの検査は緑のままである。</p>
 *
 * <p><b>荷主 ID はクライアントの指定を信じない。</b> Gateway が JWT から取り出して
 * {@code X-Auth-Shipper-Id} で伝える（[ADR-0001] 決定 4）。伝わっていなければ
 * <b>何も出さない</b>——既定を置くと、伝達が壊れていることに気づかないまま
 * 他社の請求書が見える。</p>
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class ShipperInvoiceControllerIT extends AbstractAxonIntegrationTest {

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

    private static final org.springframework.core.ParameterizedTypeReference<Map<String, Object>>
            JSON = new org.springframework.core.ParameterizedTypeReference<>() { };

    private String shipperUrl(String path) {
        return "http://localhost:" + port + "/api/v1/billing/shipper-invoices" + path;
    }

    private String accountantUrl(String path) {
        return "http://localhost:" + port + "/api/v1/billing/invoices" + path;
    }

    /** 荷主の口を叩く。**荷主 ID を渡さない場合も試せる**（null）。 */
    private ResponseEntity<Map<String, Object>> readAs(String path, String shipperId) {
        var request = rest.get().uri(shipperUrl(path));
        if (shipperId != null) {
            request = request.header("X-Auth-Shipper-Id", shipperId);
        }
        return request.retrieve().toEntity(JSON);
    }

    private Map<String, Object> accountantInvoiceOf(String bookingId) {
        ResponseEntity<Map<String, Object>> response = rest.get()
                .uri(accountantUrl("/by-booking/" + bookingId)).retrieve().toEntity(JSON);
        return response.getStatusCode().is2xxSuccessful() ? response.getBody() : null;
    }

    /** 引取まで通り、発行済になった予約を 1 件作る。 */
    private Fixture issuedBooking() {
        String suffix = String.valueOf(System.nanoTime());
        String trackingNumber = "TRK-SC" + suffix.substring(suffix.length() - 9);
        String bookingId = "B-SC-" + suffix;
        String shipperId = "SHP-SC-" + suffix;

        shipperProjection.on(new ShipperRegisteredEvent(shipperId, "CORPORATE", "山田商事",
                shipperId + "@example.com", "03-0000-0000", "東京都港区", "CT-0012",
                "0.1500"));
        cargoProjection.on(new TrackingInitializedEvent(trackingNumber, bookingId, shipperId,
                "JPTYO", "USNYC", "GENERAL", new BigDecimal("1200"),
                List.of(new TrackingInitializedEvent.Leg("V-MOL-001", "JPTYO", "SGSIN",
                                AT, AT.plusSeconds(86_400)),
                        new TrackingInitializedEvent.Leg("V-ONE-002", "SGSIN", "USNYC",
                                AT.plusSeconds(90_000), AT.plusSeconds(600_000))),
                AT), "evt-" + System.nanoTime());
        reactions.on(new CargoDeliveredEvent(trackingNumber, bookingId, AT, "USNYC"));

        await().atMost(Duration.ofSeconds(30))
                .until(() -> accountantInvoiceOf(bookingId) != null);
        String invoiceId = String.valueOf(accountantInvoiceOf(bookingId).get("invoiceId"));

        // 発行するまで荷主には見えない（算出済は社内の途中経過）。
        assertThat(rest.post().uri(accountantUrl("/" + invoiceId + "/issue"))
                .header("X-Auth-Username", "accountant01")
                .retrieve().toBodilessEntity().getStatusCode().is2xxSuccessful()).isTrue();
        await().atMost(Duration.ofSeconds(30)).until(() ->
                "INVOICED".equals(accountantInvoiceOf(bookingId).get("status")));

        return new Fixture(bookingId, invoiceId, shipperId);
    }

    private record Fixture(String bookingId, String invoiceId, String shipperId) {
    }

    @Test
    @DisplayName("D8: 荷主は自社の請求書を請求書番号で読める")
    void readsTheOwnInvoiceById() {
        Fixture fixture = issuedBooking();

        var response = readAs("/" + fixture.invoiceId(), fixture.shipperId());

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().get("statusLabel")).isEqualTo("請求済");
        assertThat(new BigDecimal(String.valueOf(response.getBody().get("totalAmount"))))
                .isEqualByComparingTo("433500");
    }

    @Test
    @DisplayName("D8: 荷主は予約番号でも開ける（請求書番号を知らない）")
    void readsTheOwnInvoiceByBooking() {
        Fixture fixture = issuedBooking();

        var response = readAs("/by-booking/" + fixture.bookingId(), fixture.shipperId());

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().get("invoiceId")).isEqualTo(fixture.invoiceId());
    }

    @Test
    @DisplayName("D8: 他社の請求書は「ありません」（403 は存在を教える）")
    void hidesAnotherShippersInvoice() {
        Fixture fixture = issuedBooking();

        assertThat(readAs("/" + fixture.invoiceId(), "SHP-OTHER").getStatusCode())
                .isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(readAs("/by-booking/" + fixture.bookingId(), "SHP-OTHER").getStatusCode())
                .as("予約から引く経路でも絞りは同じ（片方だけ緩めない）")
                .isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    @DisplayName("荷主 ID が伝わっていなければ何も出さない（既定を置かない）")
    void showsNothingWithoutTheShipperHeader() {
        Fixture fixture = issuedBooking();

        assertThat(readAs("/" + fixture.invoiceId(), null).getStatusCode())
                .as("既定を置くと、伝達が壊れていることに気づかないまま他社の請求書が見える")
                .isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(readAs("/" + fixture.invoiceId(), "  ").getStatusCode())
                .as("空白も「伝わっていない」と同じに扱う")
                .isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(readAs("/by-booking/" + fixture.bookingId(), null).getStatusCode())
                .isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(readAs("/by-booking/" + fixture.bookingId(), " ").getStatusCode())
                .isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    @DisplayName("`by-booking` が請求書番号として解釈されない（経路の並び順）")
    void doesNotTreatByBookingAsAnInvoiceId() {
        Fixture fixture = issuedBooking();

        // `/{invoiceId}` を先に置くと、この呼び出しは「請求書 by-booking」を探しに行く。
        assertThat(readAs("/by-booking/" + fixture.bookingId(), fixture.shipperId())
                .getBody().get("invoiceId"))
                .isEqualTo(fixture.invoiceId());
    }
}
