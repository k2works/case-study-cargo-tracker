package com.example.cargotracker.billing.infrastructure.projection;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.cargotracker.billing.domain.model.events.InvoiceAdjustedEvent;
import com.example.cargotracker.billing.domain.model.events.InvoiceCalculatedEvent;
import com.example.cargotracker.billing.domain.model.events.InvoiceIssuedEvent;
import com.example.cargotracker.billing.infrastructure.query.BillingQueries;
import com.example.cargotracker.billing.domain.model.events.InvoiceVoidedEvent;
import com.example.cargotracker.shared.contract.event.PaymentRecordedEvent;
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
import java.time.Month;
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

    @Autowired
    private com.example.cargotracker.billing.infrastructure.persistence.InvoiceMapper invoices;

    @Autowired
    private org.springframework.jdbc.core.JdbcTemplate jdbc;

    private static InvoiceCalculatedEvent calculated(String invoiceId, String bookingId) {
        return calculatedAt(invoiceId, bookingId, AT);
    }

    private static InvoiceCalculatedEvent calculatedAt(String invoiceId, String bookingId,
            Instant calculatedAt) {
        return new InvoiceCalculatedEvent(invoiceId, bookingId, "SHP-000001", "山田商事",
                "CORPORATE", "CT-0012", new BigDecimal("0.1500"),
                new BigDecimal("510000"), new BigDecimal("76500"), BigDecimal.ZERO,
                // 輸出免税（税率は算出時のものを載せる）。
                new BigDecimal("0.10"), true,
                new BigDecimal("433500"), "JPY", null,
                List.of(new InvoiceCalculatedEvent.LineItem("BASE",
                                "基本料金（2 区間・近海 2.5 + 遠洋 6.0・1,200 kg・一般 1.0）",
                                new BigDecimal("510000"), "JPY", null),
                        new InvoiceCalculatedEvent.LineItem("DISCOUNT", "割引（15%・CT-0012）",
                                new BigDecimal("76500"), "JPY", null),
                        new InvoiceCalculatedEvent.LineItem("TAX", "消費税（輸出免税）",
                                BigDecimal.ZERO, "JPY", null)),
                "accountant01", calculatedAt);
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

        projection.on(new InvoiceAdjustedEvent(invoiceId, "ADJ-1", null, new BigDecimal("-10000"),
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

        List<String> ids = queries.handle(new FindInvoicesQuery(false, null)).items().stream()
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

        assertThat(queries.handle(new FindInvoicesQuery(false, null)).items())
                .extracting(InvoiceSummaryView::invoiceId)
                .contains(open)
                .doesNotContain(paid);
        assertThat(queries.handle(new FindInvoicesQuery(true, null)).items())
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
        projection.on(new InvoiceAdjustedEvent(invoiceId, "ADJ-1", null, new BigDecimal("12000"),
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

    @Test
    @DisplayName("同じ調整イベントが 2 度届いても、明細は 1 行のまま")
    void doesNotDuplicateAdjustmentsOnRedelivery() {
        // **調整は入れ直せない**（算出と違って別のイベントで積む）。MAX(line_seq)+1 で
        // 採ると、2 度目は新しい番号を採って同じ内容の行がもう 1 行できる——
        // **合計は動かないのに明細だけが増える**ので、経理が二重に調整したと読む。
        // IT13 の画面から踏む検査が実際にこれを出した。
        String invoiceId = project("DUP");
        var adjusted = new InvoiceAdjustedEvent(invoiceId, "ADJ-1", null, new BigDecimal("-10000"),
                "誤配による再設計", "EX-2026-0928-03", new BigDecimal("-10000"),
                BigDecimal.ZERO, new BigDecimal("423500"), "JPY", "accountant01", AT);

        projection.on(adjusted, "evt-dup");
        projection.on(adjusted, "evt-dup");

        assertThat(queries.handle(new FindInvoiceQuery(invoiceId)).lineItems())
                .filteredOn(line -> "ADJUSTMENT".equals(line.itemType()))
                .hasSize(1);
    }

    @Test
    @DisplayName("引き継ぎ C: 取り消しは行を消さずに積み、元の調整に取り消し済みの印が付く")
    void marksTheReversedAdjustment() {
        String invoiceId = project("REV");

        projection.on(new InvoiceAdjustedEvent(invoiceId, "ADJ-1", null,
                new BigDecimal("-10000"), "符号を取り違えた減額", null,
                new BigDecimal("-10000"), BigDecimal.ZERO, new BigDecimal("423500"),
                "JPY", "accountant01", AT), "evt-r1");
        projection.on(new InvoiceAdjustedEvent(invoiceId, "ADJ-1-REV", "ADJ-1",
                new BigDecimal("10000"), "符号の誤り", null,
                BigDecimal.ZERO, BigDecimal.ZERO, new BigDecimal("433500"),
                "JPY", "accountant01", AT), "evt-r2");

        var lines = queries.handle(new FindInvoiceQuery(invoiceId)).lineItems().stream()
                .filter(line -> "ADJUSTMENT".equals(line.itemType()))
                .toList();

        // **消さずに 2 行残す。** 何が起きたかを追えない記録は根拠にならない。
        assertThat(lines).hasSize(2);
        assertThat(lines.get(0).adjustmentId()).isEqualTo("ADJ-1");
        assertThat(lines.get(0).reversed())
                .as("取り消し済みの印が付く（押せるのに断られる操作を画面に並べない）")
                .isTrue();
        assertThat(lines.get(1).description())
                .as("何の取り消しかが読める")
                .contains("取り消し");
        assertThat(lines.get(1).reversed())
                .as("取り消しそのものは取り消し済みではない")
                .isFalse();
    }

    @Test
    @DisplayName("引き継ぎ C: 同じ取り消しが 2 度届いても 1 行（少なくとも 1 回配送）")
    void doesNotDuplicateReversals() {
        String invoiceId = project("REVDUP");
        projection.on(new InvoiceAdjustedEvent(invoiceId, "ADJ-1", null,
                new BigDecimal("-10000"), "符号を取り違えた減額", null,
                new BigDecimal("-10000"), BigDecimal.ZERO, new BigDecimal("423500"),
                "JPY", "accountant01", AT), "evt-rd1");
        var reversal = new InvoiceAdjustedEvent(invoiceId, "ADJ-1-REV", "ADJ-1",
                new BigDecimal("10000"), "符号の誤り", null,
                BigDecimal.ZERO, BigDecimal.ZERO, new BigDecimal("433500"),
                "JPY", "accountant01", AT);

        projection.on(reversal, "evt-rd2");
        // **別の配送として届く場合もある**（元イベントの識別子が違う）。調整の
        // 識別子で縛っていなければ、ここで 3 行になる。
        projection.on(reversal, "evt-rd3");

        assertThat(queries.handle(new FindInvoiceQuery(invoiceId)).lineItems())
                .filteredOn(line -> "ADJUSTMENT".equals(line.itemType()))
                .hasSize(2);
    }

    @Test
    @DisplayName("US23 §1: 発行を写すと状態・発行日・期限・通知の記録が一度に入る")
    void marksIssued() {
        String invoiceId = project("ISSUE");

        projection.on(new InvoiceIssuedEvent(invoiceId, "B-ISSUE", "SHP-000001",
                new BigDecimal("433500"), "JPY",
                java.time.LocalDate.of(2026, Month.OCTOBER, 12), java.time.LocalDate.of(2026, Month.NOVEMBER, 11),
                "accountant01", AT), "evt-issue");

        var view = queries.handle(new FindInvoiceQuery(invoiceId));
        assertThat(view.status()).isEqualTo("INVOICED");
        assertThat(view.dueOn())
                .as("状態と期限を別々に書くと、片方だけ入った行が「請求済だが期限が無い」になる")
                .isEqualTo(java.time.LocalDate.of(2026, Month.NOVEMBER, 11));
        assertThat(view.issuedOn()).isEqualTo(java.time.LocalDate.of(2026, Month.OCTOBER, 12));

        // **記録と読み口は対で出す。** 通知の記録だけ書いて読めないと、
        // 「いつ何を伝えたか」が誰にも見えない。
        assertThat(invoices.findNotification(invoiceId))
                .as("送信基盤はスコープ外なので、残すのは「いつ・誰に・何を伝えたか」")
                .isNotNull()
                .satisfies(row -> assertThat(row.shipperId()).isEqualTo("SHP-000001"));
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
    @DisplayName("US23 §5: 支払期限の翌日から未払いとして出る（SQL 側の境界）")
    void listsTheInvoiceTheDayAfterItsDueDate() {
        String invoiceId = project("OVERDUE");
        issue(invoiceId, "B-OVERDUE");

        assertThat(overdueIds(java.time.LocalDate.of(2026, Month.NOVEMBER, 12)))
                .as("期限は 2026-11-11。翌日から未払い")
                .contains(invoiceId);
    }

    @Test
    @DisplayName("US23 §5: 期限当日は未払いにならない（当日中の入金はふつうにある）")
    void doesNotListTheInvoiceOnItsDueDate() {
        String invoiceId = project("DUE-TODAY");
        issue(invoiceId, "B-DUE-TODAY");

        // **判定は Java と SQL の 2 か所にある。** Java 側だけを見ると、
        // SQL の `<=` と `<` の取り違えが素通りする。
        assertThat(overdueIds(java.time.LocalDate.of(2026, Month.NOVEMBER, 11)))
                .as("当日に督促が飛ぶと、入金する側は「まだ期限内なのに」と受け取る")
                .doesNotContain(invoiceId);
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

    private java.util.List<String> overdueIds(java.time.LocalDate today) {
        return queries.handle(new BillingQueries.FindOverdueInvoicesQuery(today))
                .items().stream()
                .map(BillingQueries.InvoiceSummaryView::invoiceId)
                .toList();
    }

    /** 発行を写す。**荷主に見えるのは発行してから**なので、多くの検査がここを通る。 */
    private void issue(String invoiceId, String bookingId) {
        projection.on(new InvoiceIssuedEvent(invoiceId, bookingId, "SHP-000001",
                new BigDecimal("433500"), "JPY",
                java.time.LocalDate.of(2026, Month.OCTOBER, 12), java.time.LocalDate.of(2026, Month.NOVEMBER, 11),
                "accountant01", AT), "evt-i" + System.nanoTime());
    }

    @Test
    @DisplayName("ADR-0017 決定 2: 取消は billing_status と void_marker を同じ更新で動かす")
    void marksTheVoidedInvoice() {
        String bookingId = "B-VOID-" + System.nanoTime();
        String first = "INV-V1-" + System.nanoTime();
        projection.on(calculatedAt(first, bookingId, AT), "evt-v1");

        projection.on(new InvoiceVoidedEvent(first, bookingId, "宛先の誤り",
                "accountant01", AT), "evt-void");

        assertThat(queries.handle(new FindInvoiceQuery(first)).status())
                .as("行は消さない（消すと、取り消した事実そのものが残らない）")
                .isEqualTo("VOID");

        // **void_marker が動いていなければ、ここで UNIQUE に弾かれる。**
        // 片方だけ書く実装に戻すと、この行が入らず赤になる。
        String second = "INV-V2-" + System.nanoTime();
        projection.on(calculatedAt(second, bookingId, AT), "evt-v2");

        assertThat(queries.handle(new FindInvoiceQuery(second)))
                .as("取り消したら、同じ予約に新しい請求書を発行できる（不変条件 6）")
                .isNotNull();
    }

    @Test
    @DisplayName("違う調整は 2 行とも残る（同じ請求書に複数の根拠がある）")
    void keepsDistinctAdjustments() {
        String invoiceId = project("MULTI");

        projection.on(new InvoiceAdjustedEvent(invoiceId, "ADJ-1", null, new BigDecimal("-10000"),
                "誤配による再設計", "EX-1", new BigDecimal("-10000"), BigDecimal.ZERO,
                new BigDecimal("423500"), "JPY", "accountant01", AT), "evt-m1");
        projection.on(new InvoiceAdjustedEvent(invoiceId, "ADJ-2", null, new BigDecimal("12000"),
                "留置 4 営業日の保管料", "IMP-2026-0001", new BigDecimal("2000"),
                BigDecimal.ZERO, new BigDecimal("435500"), "JPY", "accountant01", AT), "evt-m2");

        assertThat(queries.handle(new FindInvoiceQuery(invoiceId)).lineItems())
                .filteredOn(line -> "ADJUSTMENT".equals(line.itemType()))
                .extracting(InvoiceLineView::basisExceptionId)
                .containsExactly("EX-1", "IMP-2026-0001");
    }
}
