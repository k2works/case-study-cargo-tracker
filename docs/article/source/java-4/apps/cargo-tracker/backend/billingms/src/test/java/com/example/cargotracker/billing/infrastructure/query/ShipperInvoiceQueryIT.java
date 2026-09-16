package com.example.cargotracker.billing.infrastructure.query;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.cargotracker.billing.domain.model.events.InvoiceCalculatedEvent;
import com.example.cargotracker.billing.domain.model.events.InvoiceIssuedEvent;
import com.example.cargotracker.billing.domain.model.events.InvoiceVoidedEvent;
import com.example.cargotracker.billing.infrastructure.projection.InvoiceProjection;
import com.example.cargotracker.billing.infrastructure.query.BillingQueries.FindShipperInvoiceOfBookingQuery;
import com.example.cargotracker.billing.infrastructure.query.BillingQueries.FindShipperInvoiceQuery;
import com.example.cargotracker.shared.testing.AbstractAxonIntegrationTest;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.Month;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;

/**
 * 荷主が読む自社の請求書（S62 / US23 §受入基準 2・デモ項目 D8）。
 *
 * <p><b>金額を出す唯一の荷主向け画面である。</b> 絞りを外しても経理向けの検査は
 * 緑のままなので、ここで別に固定する——<b>他社の請求書が読める</b>のは、
 * 気づかれないまま最も長く残る種類の欠陥である。</p>
 *
 * <p><b>「ありません」で返す。</b> 403 にすると、その請求書が存在することを
 * 教えてしまう。</p>
 */
@SpringBootTest
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class ShipperInvoiceQueryIT extends AbstractAxonIntegrationTest {

    private static final Instant AT = Instant.parse("2026-09-28T01:00:00Z");

    @Autowired
    private InvoiceProjection projection;

    @Autowired
    private InvoiceQueryHandler queries;

    private static InvoiceCalculatedEvent calculatedAt(String invoiceId, String bookingId,
            Instant calculatedAt) {
        return new InvoiceCalculatedEvent(invoiceId, bookingId, "SHP-000001", "山田商事",
                "CORPORATE", "CT-0012", new BigDecimal("0.1500"),
                new BigDecimal("510000"), new BigDecimal("76500"), BigDecimal.ZERO,
                new BigDecimal("0.10"), true,
                new BigDecimal("433500"), "JPY", null,
                List.of(new InvoiceCalculatedEvent.LineItem("BASE", "基本料金",
                        new BigDecimal("510000"), "JPY", null)),
                "accountant01", calculatedAt);
    }

    private String project(String suffix) {
        String invoiceId = "INV-" + suffix + "-" + System.nanoTime();
        projection.on(calculatedAt(invoiceId, "B-" + suffix + "-" + System.nanoTime(), AT),
                "evt-" + System.nanoTime());
        return invoiceId;
    }

    /** 発行を写す。**荷主に見えるのは発行してから**である。 */
    private void issue(String invoiceId, String bookingId) {
        projection.on(new InvoiceIssuedEvent(invoiceId, bookingId, "SHP-000001",
                new BigDecimal("433500"), "JPY",
                java.time.LocalDate.of(2026, Month.OCTOBER, 12), java.time.LocalDate.of(2026, Month.NOVEMBER, 11),
                "accountant01", AT), "evt-i" + System.nanoTime());
    }

    @Test
    @DisplayName("D8: 荷主は自社の請求書を読める。他社の請求書は「ありません」")
    void showsOnlyTheOwnersInvoice() {
        String invoiceId = project("SHIPPER");
        issue(invoiceId, "B-SHIPPER");

        assertThat(queries.handle(new FindShipperInvoiceQuery(invoiceId, "SHP-000001")))
                .as("発行済は荷主が読める（金額を出す唯一の荷主向け画面）")
                .isNotNull();
        assertThat(queries.handle(new FindShipperInvoiceQuery(invoiceId, "SHP-999999")))
                .as("403 ではなく「ありません」。403 はその請求書が存在することを教える")
                .isNull();
    }

    @Test
    @DisplayName("D8: 算出済と取消は荷主に見せない（確定していない金額で会話を始めない）")
    void hidesInvoicesTheShipperShouldNotSee() {
        String calculatedOnly = project("SHIPPER-CALC");
        assertThat(queries.handle(new FindShipperInvoiceQuery(calculatedOnly, "SHP-000001")))
                .as("算出済は社内の途中経過である")
                .isNull();

        String voided = project("SHIPPER-VOID");
        issue(voided, "B-SHIPPER-VOID");
        projection.on(new InvoiceVoidedEvent(voided, "B-SHIPPER-VOID", "宛先の誤り",
                "accountant01", AT), "evt-void-shipper");
        assertThat(queries.handle(new FindShipperInvoiceQuery(voided, "SHP-000001")))
                .as("一度取り消したものを見せ続けない")
                .isNull();
    }

    @Test
    @DisplayName("D8: 荷主は予約番号でも自社の請求書を開ける（請求書番号を知らない）")
    void findsTheOwnersInvoiceByBooking() {
        String bookingId = "B-SHIPPER-BK-" + System.nanoTime();
        String invoiceId = "INV-SB-" + System.nanoTime();
        projection.on(calculatedAt(invoiceId, bookingId, AT), "evt-sb");
        issue(invoiceId, bookingId);

        assertThat(queries.handle(
                new FindShipperInvoiceOfBookingQuery(bookingId, "SHP-000001")))
                .as("番号を打たせると、荷主は番号を探しに行くことになる")
                .isNotNull()
                .satisfies(view -> assertThat(view.invoiceId()).isEqualTo(invoiceId));
        assertThat(queries.handle(
                new FindShipperInvoiceOfBookingQuery(bookingId, "SHP-999999")))
                .as("予約から引く経路でも絞りは同じ（片方だけ緩めない）")
                .isNull();
    }
}
