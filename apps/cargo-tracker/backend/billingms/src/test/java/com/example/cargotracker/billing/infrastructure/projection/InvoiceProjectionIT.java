package com.example.cargotracker.billing.infrastructure.projection;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.cargotracker.billing.domain.model.events.InvoiceAdjustedEvent;
import com.example.cargotracker.billing.domain.model.events.InvoiceCalculatedEvent;
import com.example.cargotracker.billing.infrastructure.persistence.AttentionItemMapper;
import com.example.cargotracker.billing.infrastructure.query.BillingQueries.FindInvoiceOfBookingQuery;
import com.example.cargotracker.billing.infrastructure.query.BillingQueries.FindInvoiceQuery;
import com.example.cargotracker.billing.infrastructure.query.BillingQueries.FindInvoicesQuery;
import com.example.cargotracker.billing.infrastructure.query.BillingQueries.InvoiceLineView;
import com.example.cargotracker.billing.infrastructure.query.BillingQueries.InvoiceSummaryView;
import com.example.cargotracker.billing.infrastructure.query.InvoiceQueryHandler;
import com.example.cargotracker.shared.testing.AbstractAxonIntegrationTest;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;

/**
 * 請求書の投影と読み取り（US21 §受入基準 5・6／US22 §4）。
 *
 * <p>集約の検査は「集約が何を許すか」を見るもので、<b>一覧と詳細がどう見えるか</b>は
 * 判別しない。ここでは実際の PostgreSQL に書いて読み直す。</p>
 */
@SpringBootTest
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class InvoiceProjectionIT extends AbstractAxonIntegrationTest {

    private static final Instant AT = Instant.parse("2026-09-28T01:00:00Z");

    @Autowired
    private InvoiceProjection projection;

    @Autowired
    private InvoiceQueryHandler queries;

    @Autowired
    private AttentionItemMapper attentionItems;

    private static InvoiceCalculatedEvent calculated(String invoiceId, String bookingId) {
        return new InvoiceCalculatedEvent(invoiceId, bookingId, "SHP-000001", "山田商事",
                "CORPORATE", "CT-0012", new BigDecimal("0.1500"),
                new BigDecimal("510000"), new BigDecimal("76500"), BigDecimal.ZERO,
                new BigDecimal("433500"), "JPY",
                List.of(new InvoiceCalculatedEvent.LineItem("BASE",
                                "基本料金（2 区間・近海 2.5 + 遠洋 6.0・1,200 kg・一般 1.0）",
                                new BigDecimal("510000"), "JPY", null),
                        new InvoiceCalculatedEvent.LineItem("DISCOUNT", "割引（15%・CT-0012）",
                                new BigDecimal("76500"), "JPY", null),
                        new InvoiceCalculatedEvent.LineItem("TAX", "消費税（輸出免税）",
                                BigDecimal.ZERO, "JPY", null)),
                "accountant01", AT);
    }

    private String project(String suffix) {
        String invoiceId = "INV-" + suffix + "-" + System.nanoTime();
        projection.on(calculated(invoiceId, "B-" + suffix + "-" + System.nanoTime()),
                "evt-" + System.nanoTime());
        return invoiceId;
    }

    @Test
    @DisplayName("US21 §5: 算出済として登録され、根拠が明細に並ぶ")
    void projectsTheCalculatedInvoice() {
        String invoiceId = project("P1");

        var view = queries.handle(new FindInvoiceQuery(invoiceId));
        assertThat(view).isNotNull();
        assertThat(view.statusLabel()).isEqualTo("算出済");
        assertThat(view.totalAmount()).isEqualByComparingTo("433500");
        assertThat(view.shipperTypeLabel()).isEqualTo("法人");
        assertThat(view.lineItems())
                .extracting(InvoiceLineView::itemTypeLabel)
                .containsExactly("基本料金", "割引", "消費税");
        assertThat(view.lineItems().getFirst().description())
                .as("金額だけでは「なぜこの額か」に答えられない")
                .contains("2 区間").contains("1,200 kg");
        // **見積は US01（IT14）。** 本 IT では概算行も差額も出さない。
        assertThat(view.quotedAmount()).isNull();
    }

    @Test
    @DisplayName("US22 §4: 割引率と契約番号が読める")
    void showsTheDiscountBasis() {
        String invoiceId = project("D1");

        var view = queries.handle(new FindInvoiceQuery(invoiceId));
        assertThat(view.discountRate()).isEqualByComparingTo("0.1500");
        assertThat(view.contractNumber()).isEqualTo("CT-0012");
        assertThat(view.lineItems())
                .filteredOn(line -> "DISCOUNT".equals(line.itemType()))
                .singleElement()
                .satisfies(line -> assertThat(line.description()).contains("CT-0012"));
    }

    @Test
    @DisplayName("US21 §6: 調整を写すと合計が動き、根拠の例外が残る")
    void projectsAdjustmentWithItsBasis() {
        String invoiceId = project("A1");

        projection.on(new InvoiceAdjustedEvent(invoiceId, new BigDecimal("-10000"),
                "誤配による再設計", "EX-2026-0928-03", new BigDecimal("-10000"),
                BigDecimal.ZERO, new BigDecimal("423500"), "JPY", "accountant01", AT),
                "evt-" + System.nanoTime());

        var view = queries.handle(new FindInvoiceQuery(invoiceId));
        assertThat(view.adjustmentAmount()).isEqualByComparingTo("-10000");
        assertThat(view.totalAmount()).isEqualByComparingTo("423500");
        assertThat(view.lineItems())
                .filteredOn(line -> "ADJUSTMENT".equals(line.itemType()))
                .singleElement()
                .satisfies(line -> {
                    assertThat(line.basisExceptionId())
                            .as("根拠の例外を指せないと、あとから確かめられない")
                            .isEqualTo("EX-2026-0928-03");
                    assertThat(line.description()).isEqualTo("誤配による再設計");
                });
    }

    @Test
    @DisplayName("不変条件 2: 同じ予約に 2 通目の有効な請求書は残らず、経理の要確認に出る")
    void keepsAtMostOneActiveInvoicePerBooking() {
        // **画面の確認だけでは同時の 2 件が通る**（読んでからコマンドを送るので、
        // 2 つの要求が両方とも「無い」を見る）。DB の部分ユニークが最後の砦で、
        // **弾いた事実は要確認に残す**——集約は受け付けているので、記録しなければ
        // 「作ったのに一覧に出ない」が誰にも見えない。
        String booking = "B-UNQ-" + System.nanoTime();
        String first = "INV-UNQ1-" + System.nanoTime();
        String second = "INV-UNQ2-" + System.nanoTime();

        projection.on(calculated(first, booking), "evt-" + System.nanoTime());
        projection.on(calculated(second, booking), "evt-" + System.nanoTime());

        assertThat(queries.handle(new FindInvoiceOfBookingQuery(booking)))
                .satisfies(view -> assertThat(view.invoiceId()).isEqualTo(first));
        assertThat(queries.handle(new FindInvoiceQuery(second))).isNull();
        assertThat(attentionItems.findOpenByRole("ROLE_ACCOUNTANT"))
                .filteredOn(item -> second.equals(item.targetId()))
                .singleElement()
                .satisfies(item -> assertThat(item.reason()).contains(first));
    }

    @Test
    @DisplayName("一覧は既定で入金済・取消を外し、算出日時の新しい順に出る")
    void listsOpenInvoicesNewestFirst() {
        String older = project("L1");
        String newer = project("L2");

        List<String> ids = queries.handle(new FindInvoicesQuery(false, null)).items().stream()
                .map(InvoiceSummaryView::invoiceId)
                .filter(id -> id.equals(older) || id.equals(newer))
                .toList();

        // 同じ算出日時なので、並びは invoice_id の順で決まる（安定している）。
        assertThat(ids).containsExactlyInAnyOrder(older, newer);
        assertThat(queries.handle(new FindInvoicesQuery(false, null)).total())
                .isEqualTo(queries.handle(new FindInvoicesQuery(false, null)).items().size());
    }

    @Test
    @DisplayName("予約で絞れる（予約詳細から請求書へ飛ぶ）")
    void filtersByBooking() {
        String booking = "B-F1-" + System.nanoTime();
        String invoiceId = "INV-F1-" + System.nanoTime();
        projection.on(calculated(invoiceId, booking), "evt-" + System.nanoTime());

        assertThat(queries.handle(new FindInvoicesQuery(true, booking)).items())
                .singleElement()
                .satisfies(item -> assertThat(item.invoiceId()).isEqualTo(invoiceId));
    }

    @Test
    @DisplayName("同じイベントを 2 度読んでも明細は積み上がらない")
    void isIdempotent() {
        String invoiceId = "INV-IDEM-" + System.nanoTime();
        var event = calculated(invoiceId, "B-IDEM-" + System.nanoTime());

        projection.on(event, "evt-idem");
        projection.on(event, "evt-idem");

        assertThat(queries.handle(new FindInvoiceQuery(invoiceId)).lineItems()).hasSize(3);
    }

    @Test
    @DisplayName("算出を読み直しても、あとから積んだ調整行は消えない")
    void keepsAdjustmentsWhenTheCalculationIsReplayed() {
        // **調整は別のイベントで積まれる。** 算出の読み直しで明細を入れ直すとき、
        // 調整まで消すと根拠が失われる。
        String invoiceId = "INV-KEEP-" + System.nanoTime();
        var event = calculated(invoiceId, "B-KEEP-" + System.nanoTime());
        projection.on(event, "evt-keep");
        projection.on(new InvoiceAdjustedEvent(invoiceId, new BigDecimal("12000"),
                "留置 4 営業日の保管料", "IMP-2026-0001", new BigDecimal("12000"),
                BigDecimal.ZERO, new BigDecimal("445500"), "JPY", "accountant01", AT),
                "evt-adj");

        projection.on(event, "evt-keep");

        assertThat(queries.handle(new FindInvoiceQuery(invoiceId)).lineItems())
                .filteredOn(line -> "ADJUSTMENT".equals(line.itemType()))
                .singleElement()
                .satisfies(line -> assertThat(line.basisExceptionId())
                        .isEqualTo("IMP-2026-0001"));
    }
}
