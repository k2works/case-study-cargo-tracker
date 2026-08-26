package com.example.billingms.infrastructure.persistence;

import com.example.billingms.application.port.InvoiceRepository;
import com.example.billingms.domain.model.BillingBookingId;
import com.example.billingms.domain.model.BillingShipperId;
import com.example.billingms.domain.model.CancellationFee;
import com.example.billingms.domain.model.CancelledAtStatus;
import com.example.billingms.domain.model.CargoType;
import com.example.billingms.domain.model.DiscountPolicy;
import com.example.billingms.domain.model.DiscountRate;
import com.example.billingms.domain.model.Invoice;
import com.example.billingms.domain.model.InvoiceId;
import com.example.billingms.domain.model.InvoiceLineItem;
import com.example.billingms.domain.model.Money;
import com.example.billingms.domain.model.PaymentStatus;
import com.example.billingms.domain.model.TaxRate;
import com.example.billingms.domain.model.TransportCharge;
import java.util.List;
import java.util.Optional;

/**
 * 精算書の永続化（US21）。
 *
 * <p><strong>更新は書かない</strong>（[ADR-027] 決定 4）。発行した精算書の金額は動かない
 * ため、{@code save} は常に新規の書き込みである——「常に INSERT する save」が正しい
 * 唯一の場面である。
 */
public class MyBatisInvoiceRepository implements InvoiceRepository {

    private final InvoiceMapper invoices;
    private final InvoiceLineItemMapper lineItems;

    public MyBatisInvoiceRepository(InvoiceMapper invoices, InvoiceLineItemMapper lineItems) {
        this.invoices = invoices;
        this.lineItems = lineItems;
    }

    @Override
    public void save(Invoice invoice) {
        InvoiceRecord row = toRecord(invoice);
        invoices.insert(row);

        int seq = 1;
        for (InvoiceLineItem item : invoice.lineItems()) {
            InvoiceLineItemRecord itemRow = new InvoiceLineItemRecord();
            itemRow.setInvoiceId(row.getId());
            itemRow.setDescription(item.description());
            itemRow.setAmountValue(item.amount().amount());
            itemRow.setAmountCurrency(item.amount().currency());
            itemRow.setSeqNumber(seq++);
            lineItems.insert(itemRow);
        }
    }

    @Override
    public Optional<Invoice> findById(String invoiceId) {
        return Optional.ofNullable(invoices.selectByInvoiceNumber(invoiceId))
                .map(this::toDomain);
    }

    @Override
    public List<Invoice> findAll() {
        return invoices.selectAll().stream().map(this::toDomain).toList();
    }

    @Override
    public boolean existsForBooking(String bookingId) {
        return invoices.countByBookingId(bookingId) > 0;
    }

    private static InvoiceRecord toRecord(Invoice invoice) {
        InvoiceRecord row = new InvoiceRecord();
        row.setInvoiceNumber(invoice.invoiceId().value());
        row.setBookingId(invoice.cargoBookingId().value());
        row.setShipperId(invoice.shipperId().value());
        row.setShipperName(invoice.shipperName());
        row.setShipperCorporate(invoice.shipperId().isCorporate());
        row.setLegCount(invoice.charge().legCount());
        row.setWeightKg(invoice.charge().weightKg());
        row.setCargoType(invoice.charge().cargoType().name());
        row.setBaseAmountValue(invoice.baseAmount().amount());
        row.setBaseAmountCurrency(invoice.baseAmount().currency());
        // **未設定は 0% ではない**（[ADR-012]）。null のまま書く
        row.setDiscountRate(invoice.discountRate() == null ? null
                : invoice.discountRate().value());
        row.setDiscountAmountValue(invoice.discountAmount().amount());
        row.setDiscountAmountCurrency(invoice.discountAmount().currency());
        if (invoice.cancellationFee() != null) {
            row.setCancellationFeeValue(invoice.cancellationFee().amount().amount());
            row.setCancellationFeeCurrency(invoice.cancellationFee().amount().currency());
            row.setCancellationFeeRate(invoice.cancellationFee().feeRate());
            row.setBookingStatusAtCancel(
                    invoice.cancellationFee().bookingStatusAtCancel().name());
        }
        row.setTaxRate(invoice.taxRate().value());
        row.setTaxAmount(invoice.taxAmount().amount());
        row.setTotalAmountValue(invoice.totalAmount().amount());
        row.setTotalAmountCurrency(invoice.totalAmount().currency());
        row.setPaymentStatus(invoice.paymentStatus().name());
        row.setIssuedAt(invoice.issuedAt());
        return row;
    }

    private Invoice toDomain(InvoiceRecord row) {
        List<InvoiceLineItem> items = lineItems.selectByInvoiceId(row.getId()).stream()
                .map(item -> InvoiceLineItem.of(item.getDescription(),
                        Money.of(item.getAmountValue(), item.getAmountCurrency())))
                .toList();

        DiscountPolicy policy = row.getDiscountRate() == null
                ? DiscountPolicy.none()
                : DiscountPolicy.forCorporate(DiscountRate.of(row.getDiscountRate()));

        CancellationFee fee = row.getBookingStatusAtCancel() == null ? null
                : new CancellationFee(
                        CancelledAtStatus.of(row.getBookingStatusAtCancel()),
                        row.getCancellationFeeRate(),
                        Money.of(row.getCancellationFeeValue(), row.getCancellationFeeCurrency()));

        // **復元では検査しない**（新しい不変条件は既存行を壊す）
        return Invoice.restore(
                InvoiceId.of(row.getInvoiceNumber()),
                BillingBookingId.of(row.getBookingId()),
                row.isShipperCorporate()
                        ? BillingShipperId.corporate(row.getShipperId())
                        : BillingShipperId.individual(row.getShipperId()),
                row.getShipperName(),
                TransportCharge.of(row.getLegCount(), row.getWeightKg(),
                        CargoType.of(row.getCargoType())),
                policy, items, fee, TaxRate.of(row.getTaxRate()),
                PaymentStatus.valueOf(row.getPaymentStatus()), row.getIssuedAt());
    }
}
