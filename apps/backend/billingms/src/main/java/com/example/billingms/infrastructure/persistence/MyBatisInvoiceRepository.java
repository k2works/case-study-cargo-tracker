package com.example.billingms.infrastructure.persistence;

import com.example.billingms.application.internal.AlreadyInvoicedException;
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
import com.example.billingms.domain.model.InvoiceAmounts;
import com.example.billingms.domain.model.InvoiceCharges;
import com.example.billingms.domain.model.InvoiceLifecycle;
import com.example.billingms.domain.model.InvoiceLineItem;
import com.example.billingms.domain.model.Money;
import com.example.billingms.domain.model.Payment;
import com.example.billingms.domain.model.PaymentMethod;
import com.example.billingms.domain.model.PaymentStatus;
import com.example.billingms.domain.model.PortRegion;
import com.example.billingms.domain.model.TaxRate;
import com.example.billingms.domain.model.TransportCharge;
import java.util.List;
import java.util.Optional;
import org.springframework.dao.DuplicateKeyException;

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

    /**
     * 発行する。
     *
     * <p><strong>制約に当たったときも、断る理由は「すでに発行済み」である。</strong>
     * 発行済みかを見てから書くまでのあいだに、もう 1 本の要求が書き込むことがある
     * （同じ画面を 2 回押す・2 人が同時に締める）。`DuplicateKeyException` のまま
     * 外へ出すと画面に 500 が出て、経理担当者には「壊れた」としか見えない
     * ——待っても変わらないし、先に押した側では成功している。
     */
    @Override
    public void save(Invoice invoice) {
        InvoiceRecord row = toRecord(invoice);
        try {
            invoices.insert(row);
        } catch (DuplicateKeyException _) {
            throw new AlreadyInvoicedException(
                    "この予約にはすでに精算書が発行されています: "
                            + invoice.cargoBookingId().value());
        }

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
    public void confirmPayment(Invoice invoice) {
        Long id = invoices.selectIdByInvoiceNumber(invoice.invoiceId().value());
        if (id == null) {
            throw new IllegalStateException(
                    "発行されていない請求書に入金は記録できません: " + invoice.invoiceId().value());
        }
        Payment payment = invoice.payment();
        PaymentRecord row = new PaymentRecord();
        row.setInvoiceId(id);
        row.setPaidAmountValue(payment.amount().amount());
        row.setPaidAmountCurrency(payment.amount().currency());
        row.setPaidAt(payment.paidAt());
        row.setPaymentMethod(payment.method().name());
        row.setTransactionReference(payment.transactionReference());
        invoices.insertPayment(row);

        invoices.updateStatus(invoice.invoiceId().value(), invoice.paymentStatus().name());
    }

    @Override
    public void revoke(Invoice invoice) {
        int updated = invoices.updateVoided(invoice.invoiceId().value(),
                invoice.voidedAt(), invoice.voidReason());
        if (updated == 0) {
            // **すでに取り消されている。**画面を 2 回押した・2 人が同時に押した
            throw new IllegalStateException(
                    "すでに取り消されています: " + invoice.invoiceId().value());
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
        row.setDueDate(invoice.dueDate());
        row.setTaxExempt(invoice.taxRate().exempted());
        row.setLegCount(invoice.charge().legCount());
        row.setLegFactor(invoice.charge().legFactor());
        row.setLegRegion(invoice.charge().region() == null ? null
                : invoice.charge().region().name());
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
                new com.example.billingms.domain.model.InvoiceHeader(
                        InvoiceId.of(row.getInvoiceNumber()),
                        BillingBookingId.of(row.getBookingId()),
                        row.isShipperCorporate()
                                ? BillingShipperId.corporate(row.getShipperId(),
                                        row.getShipperName())
                                : BillingShipperId.individual(row.getShipperId(),
                                        row.getShipperName()),
                        row.getIssuedAt()),
                new InvoiceCharges(
                        chargeOf(row),
                        policy, fee, TaxRate.of(row.getTaxRate())),
                // **保存された金額をそのまま返す**（決定 4・IT11 レビュー 高 1）。
                // 係数から計算し直すと、基準運賃や税率を将来変えた瞬間に
                // 過去に発行した請求書の金額が黙って変わる
                new InvoiceAmounts(
                        Money.of(row.getBaseAmountValue(), row.getBaseAmountCurrency()),
                        Money.of(row.getDiscountAmountValue(), row.getDiscountAmountCurrency()),
                        Money.yen(row.getTaxAmount()),
                        Money.of(row.getTotalAmountValue(), row.getTotalAmountCurrency())),
                items, lifecycleOf(row));
    }

    /**
     * 発行したあとに起きたこと（支払い・取り消し）を復元する。
     *
     * <p>入金の記録は別表にある。<strong>無ければ未入金である。</strong>
     */
    private static InvoiceLifecycle lifecycleOf(InvoiceRecord row) {
        Payment payment = row.getPaidAt() == null ? null
                : Payment.of(Money.of(row.getPaidAmountValue(), row.getPaidAmountCurrency()),
                        row.getPaidAt(), PaymentMethod.of(row.getPaymentMethod()),
                        row.getTransactionReference());
        return new InvoiceLifecycle(PaymentStatus.valueOf(row.getPaymentStatus()),
                row.getDueDate(), payment, row.getVoidedAt(), row.getVoidReason());
    }

    /**
     * 基本料金の根拠を復元する。
     *
     * <p>旅程を持たない予約（経路が決まる前のキャンセル）も復元できる必要がある
     * ——{@code TransportCharge.of} は 0 を断るため、こちらで分ける。
     */
    private static TransportCharge chargeOf(InvoiceRecord row) {
        CargoType cargoType = CargoType.of(row.getCargoType());
        if (row.getLegCount() == 0) {
            return TransportCharge.notTransported(row.getWeightKg(), cargoType);
        }
        // 地域区分を入れる前に発行した請求書は区分を持たない（列が無かったころの行）
        PortRegion region = row.getLegRegion() == null ? null
                : PortRegion.of(row.getLegRegion());
        return TransportCharge.restored(row.getLegCount(), row.getLegFactor(), region,
                row.getWeightKg(), cargoType);
    }
}
