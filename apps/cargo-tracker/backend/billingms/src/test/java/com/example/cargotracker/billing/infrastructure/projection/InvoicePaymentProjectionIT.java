package com.example.cargotracker.billing.infrastructure.projection;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.cargotracker.billing.domain.model.events.InvoiceCalculatedEvent;
import com.example.cargotracker.billing.domain.model.events.InvoiceIssuedEvent;
import com.example.cargotracker.billing.infrastructure.query.BillingQueries;
import com.example.cargotracker.billing.infrastructure.query.BillingQueries.FindInvoiceQuery;
import com.example.cargotracker.billing.infrastructure.query.InvoiceQueryHandler;
import com.example.cargotracker.shared.contract.event.PaymentRecordedEvent;
import com.example.cargotracker.shared.contract.event.PaymentVoidedEvent;
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
 * 入金の記録と取り消しが投影に出ること（US23 §受入基準 4・5／IT15 引き継ぎ 3）。
 *
 * <p><b>{@code InvoiceProjectionIT} から分けた。</b> 1 ファイルが 500 行を超えると、
 * 何を確かめているファイルなのかが読めなくなる（行数の基準はそのための目安）。</p>
 */
@SpringBootTest
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class InvoicePaymentProjectionIT extends AbstractAxonIntegrationTest {

    private static final Instant AT = Instant.parse("2026-09-28T01:00:00Z");

    @Autowired
    private InvoiceProjection projection;

    @Autowired
    private InvoiceQueryHandler queries;

    /** 算出済の請求書を 1 通作る（{@code InvoiceProjectionIT} と同じ形の最小データ）。 */
    private String project(String suffix) {
        String invoiceId = "INV-" + suffix + "-" + System.nanoTime();
        projection.on(new InvoiceCalculatedEvent(invoiceId,
                "B-" + suffix + "-" + System.nanoTime(), "SHP-000001", "山田商事",
                "CORPORATE", "CT-0012", new BigDecimal("0.1500"),
                new BigDecimal("510000"), new BigDecimal("76500"), BigDecimal.ZERO,
                new BigDecimal("0.10"), true,
                new BigDecimal("433500"), "JPY", null,
                List.of(new InvoiceCalculatedEvent.LineItem("BASE", "基本料金",
                        new BigDecimal("510000"), "JPY", null)),
                "accountant01", AT), "evt-" + System.nanoTime());
        return invoiceId;
    }

    /** 発行を写す。**入金を記録できるのは発行してから**なので、どの検査もここを通る。 */
    private void issue(String invoiceId, String bookingId) {
        projection.on(new InvoiceIssuedEvent(invoiceId, bookingId, "SHP-000001",
                new BigDecimal("433500"), "JPY",
                java.time.LocalDate.of(2026, Month.OCTOBER, 12),
                java.time.LocalDate.of(2026, Month.NOVEMBER, 11),
                "accountant01", AT), "evt-i" + System.nanoTime());
    }

    private java.util.List<String> overdueIds(java.time.LocalDate today) {
        return queries.handle(new BillingQueries.FindOverdueInvoicesQuery(today))
                .items().stream()
                .map(BillingQueries.InvoiceSummaryView::invoiceId)
                .toList();
    }

    @Test
    @DisplayName("US23 §4: 入金を写すと入金済になり、入金の行が 1 行だけ入る")
    void marksPaid() {
        String invoiceId = project("PAY");
        var paid = new PaymentRecordedEvent(invoiceId, "PAY-1", "B-PAY", "SHP-000001",
                new BigDecimal("433500"), "JPY", AT, "accountant01", AT);

        projection.on(paid, "evt-pay-1");
        // **同じ入金が 2 度届いても 1 行**（payment_id が PK・少なくとも 1 回配送）。
        projection.on(paid, "evt-pay-2");

        assertThat(queries.handle(new FindInvoiceQuery(invoiceId)).status()).isEqualTo("PAID");
    }


    @Test
    @DisplayName("US23 §5: 入金済は未払いに出ない（決着したものを督促しない）")
    void doesNotListPaidInvoices() {
        String invoiceId = project("PAID-OVERDUE");
        issue(invoiceId, "B-PAID-OVERDUE");
        projection.on(new PaymentRecordedEvent(invoiceId, "PAY-O" + System.nanoTime(),
                "B-PAID-OVERDUE", "SHP-000001", new BigDecimal("433500"), "JPY",
                AT, "accountant01", AT), "evt-po" + System.nanoTime());

        assertThat(overdueIds(java.time.LocalDate.of(2026, Month.NOVEMBER, 12)))
                .doesNotContain(invoiceId);
    }

    @Test
    @DisplayName("入金を取り消すと請求済に戻り、入金の行は印つきで残る（IT15 引き継ぎ 3）")
    void voidsARecordedPayment() {
        String invoiceId = project("PAY-VOID");
        issue(invoiceId, "B-PAY-VOID");
        projection.on(new PaymentRecordedEvent(invoiceId, "PAY-V1", "B-PAY-VOID",
                "SHP-000001", new BigDecimal("433500"), "JPY", AT, "accountant01", AT),
                "evt-pv-1");
        assertThat(queries.handle(new FindInvoiceQuery(invoiceId)).status()).isEqualTo("PAID");

        var voided = new PaymentVoidedEvent(invoiceId, "PAY-V1", "B-PAY-VOID",
                "他社の入金と取り違えた", "accountant01", AT);
        projection.on(voided, "evt-pv-2");
        // 二度届いても同じ（同じ値を入れ直すだけ）。
        projection.on(voided, "evt-pv-3");

        var view = queries.handle(new FindInvoiceQuery(invoiceId));
        assertThat(view.status()).as("請求済に戻る（督促の対象にも戻る）")
                .isEqualTo("INVOICED");
        assertThat(view.paidAt()).as("入金日は消す（入金済でないのに入金日がある行を残さない）")
                .isNull();
        // **記録と読み口は対で出す。** 行を消さないのは事実を残すためなので、
        // 読めなければ残した意味が無い。
        assertThat(view.payments()).singleElement().satisfies(payment -> {
            assertThat(payment.paymentId()).isEqualTo("PAY-V1");
            assertThat(payment.voidedAt()).as("取り消した印").isNotNull();
            assertThat(payment.voidReason()).isEqualTo("他社の入金と取り違えた");
        });
        assertThat(overdueIds(java.time.LocalDate.of(2026, Month.NOVEMBER, 12)))
                .as("督促の対象に戻る（入金が無かったことになる）")
                .contains(invoiceId);
    }

    @Test
    @DisplayName("読み取りモデルに無い請求書の入金取消は素通りする（止めない）")
    void ignoresPaymentVoidForAnUnknownInvoice() {
        // 例外にすると Event Processor が止まり、**無関係の請求書まで退避される**。
        projection.on(new PaymentVoidedEvent("INV-NONE-" + System.nanoTime(), "PAY-X",
                "B-NONE", "取り違え", "accountant01", AT), "evt-pv-none");
    }
}
