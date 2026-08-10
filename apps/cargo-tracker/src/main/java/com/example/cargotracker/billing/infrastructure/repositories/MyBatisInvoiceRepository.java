package com.example.cargotracker.billing.infrastructure.repositories;

import com.example.cargotracker.billing.domain.model.Adjustment;
import com.example.cargotracker.billing.domain.model.BillingBookingId;
import com.example.cargotracker.billing.domain.model.BilledParty;
import com.example.cargotracker.billing.domain.model.BillingShipperId;
import com.example.cargotracker.billing.domain.model.ChargeStatus;
import com.example.cargotracker.billing.domain.model.DiscountRate;
import com.example.cargotracker.billing.domain.model.Invoice;
import com.example.cargotracker.billing.domain.model.InvoiceAmounts;
import com.example.cargotracker.billing.domain.model.InvoiceId;
import com.example.cargotracker.billing.domain.model.InvoiceParties;
import com.example.cargotracker.billing.domain.model.Money;
import com.example.cargotracker.billing.domain.repository.InvoiceRepository;
import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Repository;

/** {@link InvoiceRepository} の MyBatis 実装（US21 / US22）。 */
@Repository
public class MyBatisInvoiceRepository implements InvoiceRepository {

    /** 精算書番号の形式（{@code INV-} ＋ 8 桁）。 */
    private static final String NUMBER_FORMAT = "INV-%08d";

    private final InvoiceMapper mapper;

    public MyBatisInvoiceRepository(InvoiceMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public long save(Invoice invoice) {
        InvoiceRecord row = toRecord(invoice);
        mapper.insert(row);
        return row.getId();
    }

    @Override
    public boolean update(Invoice invoice) {
        return mapper.update(toRecord(invoice)) == 1;
    }

    @Override
    public Optional<Invoice> findByInvoiceId(InvoiceId invoiceId) {
        return Optional.ofNullable(mapper.findByInvoiceNumber(invoiceId.value()))
                .map(MyBatisInvoiceRepository::toDomain);
    }

    @Override
    public Optional<Invoice> findByBookingId(BillingBookingId bookingId) {
        UUID id;
        try {
            id = UUID.fromString(bookingId.value());
        } catch (IllegalArgumentException e) {
            // **形式の違う ID を例外にしない。** 「無い」と答える
            return Optional.empty();
        }
        return Optional.ofNullable(mapper.findByBookingId(id))
                .map(MyBatisInvoiceRepository::toDomain);
    }

    @Override
    public java.util.List<Invoice> findByPaymentStatus(String paymentStatus) {
        return mapper.findByPaymentStatus(paymentStatus).stream()
                .map(MyBatisInvoiceRepository::toDomain)
                .toList();
    }

    @Override
    public boolean updateSettlement(Invoice invoice) {
        return mapper.updateSettlement(toRecord(invoice)) == 1;
    }

    @Override
    @org.springframework.transaction.annotation.Transactional
    public boolean savePayment(Invoice invoice) {
        // **状態を先に更新する。** 楽観的ロックで弾かれたら入金を書かない。
        // 逆にすると、更新に失敗した請求書に入金だけが残る
        if (mapper.updateSettlement(toRecord(invoice)) != 1) {
            return false;
        }
        InvoiceRecord row = mapper.findByInvoiceNumber(invoice.invoiceId().value());
        PaymentRecord payment = new PaymentRecord();
        payment.setInvoiceId(row.getId());
        payment.setPaidAmountValue(invoice.payment().paidAmount().value());
        payment.setPaidAmountCurrency(invoice.payment().paidAmount().currency());
        payment.setPaidAt(invoice.payment().paidAt());
        payment.setPaymentMethod(invoice.payment().method().name());
        payment.setTransactionReference(invoice.payment().transactionReference());
        mapper.insertPayment(payment);
        return true;
    }

    @Override
    public InvoiceId nextInvoiceId() {
        // **採番はシーケンスに任せる。** MAX+1 を数えると同時発行で衝突する
        return InvoiceId.of(NUMBER_FORMAT.formatted(mapper.nextSequence()));
    }

    private static InvoiceRecord toRecord(Invoice invoice) {
        InvoiceAmounts amounts = invoice.amounts();
        InvoiceRecord row = new InvoiceRecord();
        row.setInvoiceNumber(invoice.invoiceId().value());
        row.setBookingId(UUID.fromString(invoice.cargoBookingId().value()));
        row.setShipperId(UUID.fromString(invoice.shipperId().value()));
        row.setBaseAmountValue(amounts.baseAmount().value().intValueExact());
        row.setBaseAmountCurrency(amounts.baseAmount().currency());
        row.setDiscountRate(amounts.discountRate().value());
        row.setDiscountAmountValue(amounts.discountAmount().value().intValueExact());
        row.setDiscountAmountCurrency(amounts.discountAmount().currency());
        row.setTaxRate(amounts.taxRate());
        row.setTaxAmountValue(amounts.taxAmount().value().intValueExact());
        row.setTaxAmountCurrency(amounts.taxAmount().currency());
        row.setTotalAmountValue(amounts.totalAmount().value().intValueExact());
        row.setTotalAmountCurrency(amounts.totalAmount().currency());
        row.setChargeStatus(invoice.chargeStatus().name());
        row.setShipperName(invoice.parties().billed().shipperName());
        // **法人かどうかを割引率から逆算しない**（C6）。率 0% の法人が個人になる
        row.setCorporate(invoice.corporate());
        // **精算（US23）。** 発行前は null であり、そのまま書く
        if (invoice.isIssued()) {
            row.setIssuedAt(invoice.issuance().issuedAt());
            row.setDueDate(invoice.issuance().dueDate());
        }
        row.setPaymentStatus(invoice.paymentStatus() == null
                ? com.example.cargotracker.billing.domain.model.PaymentStatus.PENDING.name()
                : invoice.paymentStatus().name());
        row.setTrackingNumber(invoice.parties().billed().trackingNumber());
        Adjustment adjustment = invoice.adjustment();
        if (adjustment != null) {
            row.setAdjustmentReductionValue(adjustment.reduction().value().intValueExact());
            row.setAdjustmentCompensationValue(
                    adjustment.compensation().value().intValueExact());
            row.setAdjustmentCurrency(adjustment.reduction().currency());
            row.setAdjustmentReason(adjustment.reason());
        }
        row.setVersion(invoice.version());
        return row;
    }

    /**
     * 永続化された行から復元する。
     *
     * <p><strong>再計算しない。</strong> 保存された金額をそのまま読む。
     * 再計算すると、税率が変わった日に過去の請求書がすべて書き換わる。
     *
     * <p><strong>調整を持たない行も読める。</strong> 新しい不変条件で
     * 既存の行を読めなくしない。
     *
     * <p><strong>クエリサービスからも使う</strong>（C4）。一覧が行をまるごと
     * 受け取って組み立てるため、復元の経路を 1 つに保つ。
     */
    static Invoice toDomain(InvoiceRecord row) {
        Money base = money(row.getBaseAmountValue(), row.getBaseAmountCurrency());
        Money discount = row.getDiscountAmountValue() == null
                ? Money.zeroYen()
                : money(row.getDiscountAmountValue(), row.getDiscountAmountCurrency());

        Adjustment adjustment = null;
        if (row.getAdjustmentReason() != null) {
            adjustment = new Adjustment(
                    money(row.getAdjustmentReductionValue(), row.getAdjustmentCurrency()),
                    money(row.getAdjustmentCompensationValue(), row.getAdjustmentCurrency()),
                    row.getAdjustmentReason());
        }

        // **保存された値をそのまま使う**（C6）。割引率から逆算すると、
        // 契約はあるが割引条件が未登録の法人（率 0%）が個人として復元される
        boolean corporate = row.isCorporate();

        return Invoice.reconstruct(
                new InvoiceParties(
                        InvoiceId.of(row.getInvoiceNumber()),
                        new BillingBookingId(row.getBookingId().toString()),
                        new BillingShipperId(row.getShipperId().toString(), corporate),
                        // **凍結した宛名をそのまま読む。** 引き直さない（C7）。
                        // 古い行は名前を持たないため空になる（読み戻す側は拒まない）
                        new BilledParty(row.getShipperName(), row.getTrackingNumber())),
                new InvoiceAmounts(
                        base,
                        DiscountRate.of(row.getDiscountRate() == null
                                ? BigDecimal.ZERO : row.getDiscountRate()),
                        discount,
                        row.getTaxRate(),
                        money(row.getTaxAmountValue(), row.getTaxAmountCurrency()),
                        money(row.getTotalAmountValue(), row.getTotalAmountCurrency())),
                adjustment,
                ChargeStatus.valueOf(row.getChargeStatus()),
                row.getVersion())
                // **精算の状態は保存された値をそのまま読む**（US23）。
                // 発行していない請求書は issued_at が無く、そのまま未発行になる
                .withSettlement(issuance(row), paymentStatus(row), payment(row));
    }

    /** 発行の内容。<strong>列が無かったころの行・未発行は {@code null}</strong>。 */
    private static com.example.cargotracker.billing.domain.model.Issuance issuance(
            InvoiceRecord row) {
        if (row.getIssuedAt() == null || row.getDueDate() == null) {
            return null;
        }
        return new com.example.cargotracker.billing.domain.model.Issuance(
                row.getIssuedAt(), row.getDueDate());
    }

    /**
     * 支払いの状態。
     *
     * <p><strong>読めない値で画面を落とさない。</strong> 未知の状態は未入金として扱う
     * — 督促の一覧に出るほうが、請求書が開けないより業務が続く。
     */
    private static com.example.cargotracker.billing.domain.model.PaymentStatus paymentStatus(
            InvoiceRecord row) {
        try {
            return com.example.cargotracker.billing.domain.model.PaymentStatus
                    .of(row.getPaymentStatus());
        } catch (IllegalArgumentException e) {
            return com.example.cargotracker.billing.domain.model.PaymentStatus.PENDING;
        }
    }

    /** 入金の記録。<strong>入金確認前は {@code null}</strong>。 */
    private static com.example.cargotracker.billing.domain.model.Payment payment(
            InvoiceRecord row) {
        if (row.getPaidAt() == null || row.getPaidAmountValue() == null) {
            return null;
        }
        return new com.example.cargotracker.billing.domain.model.Payment(
                new Money(row.getPaidAmountValue(),
                        row.getPaidAmountCurrency() == null
                                ? Money.JPY : row.getPaidAmountCurrency()),
                row.getPaidAt(),
                com.example.cargotracker.billing.domain.model.PaymentMethod
                        .of(row.getPaymentMethod()),
                row.getTransactionReference());
    }

    private static Money money(Integer value, String currency) {
        return new Money(
                BigDecimal.valueOf(value == null ? 0L : value.longValue()),
                currency == null ? Money.JPY : currency);
    }
}
