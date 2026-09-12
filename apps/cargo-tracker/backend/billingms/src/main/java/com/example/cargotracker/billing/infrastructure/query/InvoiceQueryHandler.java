package com.example.cargotracker.billing.infrastructure.query;

import com.example.cargotracker.billing.domain.model.valueobjects.BillingStatus;
import com.example.cargotracker.billing.domain.model.valueobjects.LineItemType;
import com.example.cargotracker.billing.domain.model.valueobjects.PaymentTerm;
import com.example.cargotracker.billing.domain.model.valueobjects.ShipperType;
import com.example.cargotracker.billing.infrastructure.persistence.InvoiceMapper;
import com.example.cargotracker.billing.infrastructure.query.BillingQueries.FindInvoiceOfBookingQuery;
import com.example.cargotracker.billing.infrastructure.query.BillingQueries.FindInvoiceQuery;
import com.example.cargotracker.billing.infrastructure.query.BillingQueries.FindInvoicesQuery;
import com.example.cargotracker.billing.infrastructure.query.BillingQueries.FindOverdueInvoicesQuery;
import com.example.cargotracker.billing.infrastructure.query.BillingQueries.FindShipperInvoiceQuery;
import com.example.cargotracker.billing.infrastructure.query.BillingQueries.InvoiceLineView;
import com.example.cargotracker.billing.infrastructure.query.BillingQueries.InvoiceListView;
import com.example.cargotracker.billing.infrastructure.query.BillingQueries.InvoiceSummaryView;
import com.example.cargotracker.billing.infrastructure.query.BillingQueries.InvoiceView;
import java.util.List;
import org.axonframework.messaging.queryhandling.annotation.QueryHandler;
import org.springframework.stereotype.Component;

/** 請求の読み取り（S60・S61）。 */
@Component
public class InvoiceQueryHandler {

    /** 一覧の上限。**画面が読める量で切る**（絞り込みは呼ぶ側が決める）。 */
    private static final int LIMIT = 200;

    private final InvoiceMapper invoices;
    private final java.time.Clock clock;

    public InvoiceQueryHandler(InvoiceMapper invoices, java.time.Clock clock) {
        this.invoices = invoices;
        this.clock = clock;
    }

    /**
     * 業務タイムゾーンの今日。
     *
     * <p><b>DB の {@code CURRENT_DATE} を使わない。</b> サーバのタイムゾーンで
     * 判断されると、時差の分だけ 1 日早く督促が飛ぶ時間帯ができる。</p>
     */
    private java.time.LocalDate today() {
        return java.time.LocalDate.ofInstant(clock.instant(),
                com.example.cargotracker.shared.infrastructure.time
                        .BusinessClockConfiguration.BUSINESS_ZONE);
    }

    @QueryHandler
    public InvoiceListView handle(FindInvoicesQuery query) {
        List<InvoiceSummaryView> items = invoices
                .search(query.includeSettled(), query.bookingId(), LIMIT).stream()
                .map(row -> toSummary(row, today()))
                .toList();
        return new InvoiceListView(items, items.size());
    }

    @QueryHandler
    public InvoiceView handle(FindInvoiceQuery query) {
        InvoiceMapper.InvoiceRow row = invoices.find(query.invoiceId());
        return row == null ? null : toView(row);
    }

    @QueryHandler
    public InvoiceView handle(FindInvoiceOfBookingQuery query) {
        InvoiceMapper.InvoiceRow row = invoices.findActiveByBooking(query.bookingId());
        return row == null ? null : toView(row);
    }

    /**
     * 未払いの請求書（US23 §受入基準 5）。
     *
     * <p><b>絞りは SQL に置く。</b> 全件を読んでから画面で数えると、上限の
     * 打ち切りで未払いが漏れる。</p>
     */
    @QueryHandler
    public InvoiceListView handle(FindOverdueInvoicesQuery query) {
        java.time.LocalDate today = query.today() == null ? today() : query.today();
        List<InvoiceSummaryView> items = invoices.findOverdue(today).stream()
                .map(row -> toSummary(row, today))
                .toList();
        return new InvoiceListView(items, items.size());
    }

    /**
     * 荷主が読む自社の請求書（S62 / US23 §受入基準 2）。
     *
     * <p><b>荷主 ID をサーバで突き合わせる。</b> 他社の請求書は読めない——
     * 金額を出す唯一の荷主向け画面なので、絞りを画面に任せない。</p>
     *
     * <p><b>発行済と入金済だけを出す。</b> 算出済は社内の途中経過で、荷主に
     * 見せるものではない。取消も出さない（一度取り消したものを見せ続けない）。</p>
     */
    @QueryHandler
    public InvoiceView handle(FindShipperInvoiceQuery query) {
        InvoiceMapper.InvoiceRow row = invoices.find(query.invoiceId());
        if (row == null || !row.shipperId().equals(query.shipperId())) {
            return null;
        }
        BillingStatus status = BillingStatus.valueOf(row.billingStatus());
        if (status != BillingStatus.INVOICED && status != BillingStatus.PAID) {
            return null;
        }
        return toView(row);
    }

    private static InvoiceSummaryView toSummary(InvoiceMapper.InvoiceRow row,
            java.time.LocalDate today) {
        BillingStatus status = BillingStatus.valueOf(row.billingStatus());
        return new InvoiceSummaryView(row.invoiceId(), row.bookingId(), row.shipperId(),
                row.shipperName(), ShipperType.of(row.shipperType()).label(),
                row.billingStatus(), status.label(), row.totalAmount(), row.currency(),
                row.calculatedAt(), row.dueOn(),
                // **判定は 1 か所**（PaymentTerm）。集約と別々に書かない。
                PaymentTerm.overdue(status, row.dueOn(), today));
    }

    private InvoiceView toView(InvoiceMapper.InvoiceRow row) {
        BillingStatus status = BillingStatus.valueOf(row.billingStatus());
        var lineRows = invoices.findLineItems(row.invoiceId());
        // 取り消された調整の識別子。**行を消さずに印を付ける**——何が起きたかを
        // 追えない記録は、経理にとって根拠にならない。
        var reversedIds = lineRows.stream()
                .map(InvoiceMapper.LineItemRow::reversedAdjustmentId)
                .filter(java.util.Objects::nonNull)
                .collect(java.util.stream.Collectors.toSet());
        List<InvoiceLineView> lines = lineRows.stream()
                .map(line -> new InvoiceLineView(line.itemType(),
                        LineItemType.valueOf(line.itemType()).label(), line.description(),
                        line.amount(), line.currency(), line.basisExceptionId(),
                        line.adjustmentId(),
                        line.adjustmentId() != null && reversedIds.contains(line.adjustmentId())))
                .toList();
        return new InvoiceView(row.invoiceId(), row.bookingId(), row.shipperId(),
                row.shipperName(), row.shipperType(), ShipperType.of(row.shipperType()).label(),
                row.contractNumber(), row.discountRate(), row.baseAmount(), row.discountAmount(),
                row.adjustmentAmount(), row.taxAmount(), row.totalAmount(), row.currency(),
                row.billingStatus(), status.label(), row.calculatedAt(),
                // 見積時の概算（注 N12）。**見積を経ない予約では null** で、
                // S61 は概算行と差額を出さない。
                row.quotedAmount(),
                row.issuedOn(), row.dueOn(), row.paidAt(),
                // **判定は 1 か所**（PaymentTerm）。集約と別々に書かない。
                PaymentTerm.overdue(status, row.dueOn(), today()),
                lines);
    }
}
