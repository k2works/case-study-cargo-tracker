package com.example.cargotracker.billing.infrastructure.query;

import com.example.cargotracker.billing.domain.model.valueobjects.BillingStatus;
import com.example.cargotracker.billing.domain.model.valueobjects.LineItemType;
import com.example.cargotracker.billing.domain.model.valueobjects.ShipperType;
import com.example.cargotracker.billing.infrastructure.persistence.InvoiceMapper;
import com.example.cargotracker.billing.infrastructure.query.BillingQueries.FindInvoiceOfBookingQuery;
import com.example.cargotracker.billing.infrastructure.query.BillingQueries.FindInvoiceQuery;
import com.example.cargotracker.billing.infrastructure.query.BillingQueries.FindInvoicesQuery;
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

    public InvoiceQueryHandler(InvoiceMapper invoices) {
        this.invoices = invoices;
    }

    @QueryHandler
    public InvoiceListView handle(FindInvoicesQuery query) {
        List<InvoiceSummaryView> items = invoices
                .search(query.includeSettled(), query.bookingId(), LIMIT).stream()
                .map(InvoiceQueryHandler::toSummary)
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

    private static InvoiceSummaryView toSummary(InvoiceMapper.InvoiceRow row) {
        BillingStatus status = BillingStatus.valueOf(row.billingStatus());
        return new InvoiceSummaryView(row.invoiceId(), row.bookingId(), row.shipperId(),
                row.shipperName(), ShipperType.of(row.shipperType()).label(),
                row.billingStatus(), status.label(), row.totalAmount(), row.currency(),
                row.calculatedAt());
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
                // **見積は US01（IT14）。** 本 IT では常に null で、S61 は
                // 概算行と差額を出さない（注 N5）。
                null, lines);
    }
}
