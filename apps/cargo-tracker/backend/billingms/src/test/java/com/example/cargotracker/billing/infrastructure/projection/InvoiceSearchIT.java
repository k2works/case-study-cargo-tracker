package com.example.cargotracker.billing.infrastructure.projection;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.cargotracker.billing.domain.model.events.InvoiceCalculatedEvent;
import com.example.cargotracker.billing.infrastructure.query.BillingQueries.FindInvoicesQuery;
import com.example.cargotracker.billing.infrastructure.query.BillingQueries.InvoiceSummaryView;
import com.example.cargotracker.billing.infrastructure.query.InvoiceQueryHandler;
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
 * 請求一覧の並びと絞り込み（S60 / US21・US23 §受入基準 5・IT13 引き継ぎ D）。
 *
 * <p><b>{@code InvoiceProjectionIT} から分けた。</b> 1 ファイルが 500 行を超えると、
 * 何を確かめているファイルなのかが読めなくなる（行数の基準はそのための目安）。
 * <b>「投影が何を書くか」と「一覧がどう見えるか」は別の責務</b>で、切り口もそこに置いた。</p>
 */
@SpringBootTest
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class InvoiceSearchIT extends AbstractAxonIntegrationTest {

    private static final Instant AT = Instant.parse("2026-09-28T01:00:00Z");

    @Autowired
    private InvoiceProjection projection;

    @Autowired
    private InvoiceQueryHandler queries;

    @Autowired
    private org.springframework.jdbc.core.JdbcTemplate jdbc;

    private static InvoiceCalculatedEvent calculated(String invoiceId, String bookingId) {
        return calculatedAt(invoiceId, bookingId, AT);
    }

    /** 算出日時を指定した算出イベント（並びと期間の絞り込みを見るため）。 */
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
        projection.on(calculated(invoiceId, "B-" + suffix + "-" + System.nanoTime()),
                "evt-" + System.nanoTime());
        return invoiceId;
    }

    @Test
    @DisplayName("一覧は算出日時の新しい順に出る（古い順にすると赤になる）")
    void listsInvoicesNewestFirst() {
        // **同じ時刻の 2 件では順序を判別しない**（IT13 のレビューで実測。
        // `containsExactlyInAnyOrder` は `ORDER BY` を消しても緑だった）。
        String booking = "B-ORD-" + System.nanoTime();
        String older = "INV-ORD1-" + System.nanoTime();
        String newer = "INV-ORD2-" + System.nanoTime();
        projection.on(calculatedAt(older, booking + "-a", AT), "evt-" + System.nanoTime());
        projection.on(calculatedAt(newer, booking + "-b", AT.plusSeconds(3600)),
                "evt-" + System.nanoTime());

        List<String> ids = queries.handle(new FindInvoicesQuery(false, null, null, null, null)).items().stream()
                .map(InvoiceSummaryView::invoiceId)
                .filter(id -> id.equals(older) || id.equals(newer))
                .toList();

        assertThat(ids).containsExactly(newer, older);
    }

    @Test
    @DisplayName("一覧は既定で入金済・取消を外す（外さない実装に戻すと赤になる）")
    void excludesSettledInvoicesByDefault() {
        // **IT13 では PAID / VOID に至らない**ので、状態を直接書いて確かめる。
        // 確かめずに置くと、US23 で入金が入った瞬間に決着済みが一覧へ混ざる
        // （一覧は「まだ手を入れる場所」でなくなる）。
        String paid = "INV-PAID-" + System.nanoTime();
        String open = "INV-OPEN-" + System.nanoTime();
        projection.on(calculated(paid, "B-PAID-" + System.nanoTime()),
                "evt-" + System.nanoTime());
        projection.on(calculated(open, "B-OPEN-" + System.nanoTime()),
                "evt-" + System.nanoTime());
        jdbc.update("UPDATE invoice SET billing_status = 'PAID' WHERE invoice_id = ?", paid);

        assertThat(queries.handle(new FindInvoicesQuery(false, null, null, null, null)).items())
                .extracting(InvoiceSummaryView::invoiceId)
                .contains(open)
                .doesNotContain(paid);
        assertThat(queries.handle(new FindInvoicesQuery(true, null, null, null, null)).items())
                .extracting(InvoiceSummaryView::invoiceId)
                .as("切り替えれば出る（隠しっぱなしにしない）")
                .contains(paid);
    }

    @Test
    @DisplayName("予約で絞れる（予約詳細から請求書へ飛ぶ）")
    void filtersByBooking() {
        String booking = "B-F1-" + System.nanoTime();
        String invoiceId = "INV-F1-" + System.nanoTime();
        projection.on(calculated(invoiceId, booking), "evt-" + System.nanoTime());

        assertThat(queries.handle(new FindInvoicesQuery(true, booking, null, null, null)).items())
                .singleElement()
                .satisfies(item -> assertThat(item.invoiceId()).isEqualTo(invoiceId));
    }

    @Test
    @DisplayName("期間と荷主で絞り込め、合計金額が出る（IT13 引き継ぎ D・締めの仕事）")
    void filtersByPeriodAndShipperWithTotal() {
        // **締めは「その月に算出したぶん」を数える仕事である。** 一覧を目で拾って
        // 電卓を叩かせると、件数が増えるほど取りこぼす。**数えるのはサーバ**——
        // 画面で足すと、上限（200 件）で切れたぶんが静かに合計から落ちる。
        String shipper = "SHP-D" + System.nanoTime() % 100000;
        String inside = "INV-D1-" + System.nanoTime();
        String outside = "INV-D2-" + System.nanoTime();
        projection.on(shipped(inside, "B-D1-" + System.nanoTime(), shipper,
                Instant.parse("2026-09-15T01:00:00Z")), "evt-" + System.nanoTime());
        projection.on(shipped(outside, "B-D2-" + System.nanoTime(), shipper,
                Instant.parse("2026-10-02T01:00:00Z")), "evt-" + System.nanoTime());

        var september = queries.handle(new FindInvoicesQuery(true, null, shipper,
                java.time.LocalDate.of(2026, Month.SEPTEMBER, 1),
                java.time.LocalDate.of(2026, Month.SEPTEMBER, 30)));

        assertThat(september.items())
                .extracting(InvoiceSummaryView::invoiceId)
                .as("期間の外は数えない")
                .containsExactly(inside);
        assertThat(september.totalAmount())
                .as("締めの母数。**サーバが数える**")
                .isEqualByComparingTo("433500");
    }

    @Test
    @DisplayName("絞り込まなければ、その荷主以外も出る（絞り込みが効いていることの裏返し）")
    void doesNotFilterWithoutCriteria() {
        // **片方だけ見ると「いつも 1 件」でも緑になる。**
        String shipper = "SHP-D" + System.nanoTime() % 100000;
        String invoiceId = "INV-D3-" + System.nanoTime();
        projection.on(shipped(invoiceId, "B-D3-" + System.nanoTime(), shipper,
                Instant.parse("2026-09-15T01:00:00Z")), "evt-" + System.nanoTime());

        assertThat(queries.handle(new FindInvoicesQuery(true, null,
                "SHP-NOBODY", null, null)).items())
                .as("別の荷主で絞れば出ない")
                .noneSatisfy(item -> assertThat(item.invoiceId()).isEqualTo(invoiceId));
        assertThat(queries.handle(new FindInvoicesQuery(true, null, null, null, null)).items())
                .extracting(InvoiceSummaryView::invoiceId)
                .as("絞らなければ出る")
                .contains(invoiceId);
    }

    /** 荷主と算出日時を指定した算出イベント（締めの絞り込みを見るため）。 */
    private static InvoiceCalculatedEvent shipped(String invoiceId, String bookingId,
            String shipperId, Instant calculatedAt) {
        var base = calculatedAt(invoiceId, bookingId, calculatedAt);
        return new InvoiceCalculatedEvent(base.invoiceId(), base.bookingId(), shipperId,
                base.shipperName(), base.shipperType(), base.contractNumber(),
                base.discountRate(), base.baseAmount(), base.discountAmount(),
                base.taxAmount(), base.taxRate(), base.taxExempt(),
                base.totalAmount(), base.currency(), base.quotedAmount(),
                base.lineItems(), base.calculatedBy(), base.calculatedAt());
    }
}
