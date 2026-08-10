package com.example.cargotracker.billing.domain.model;

import java.math.BigDecimal;

/**
 * 精算書（US21 / US22）。Billing Context の集約ルート。
 *
 * <p><strong>算出と確定を分ける。</strong> 受入基準「算出結果を確認して確定操作が
 * できる」は、経理担当者が目で見て確かめる場を求めている。
 * 自動で確定すると確認の余地が無い。
 *
 * <p><strong>丸め後の値を保持し、再計算で導出しない</strong>
 * （{@code domain-model.md}「金額の丸め規則」）。税率や係数が将来変わっても、
 * 発行済み請求書の金額は変わってはならない。導出にすると、
 * <strong>税制改正の日に過去の請求書がすべて書き換わる</strong>。
 * <strong>税率そのものも保持する</strong> — 金額だけでは根拠を再現できない。
 *
 * <p><strong>段階丸めの順序を守る</strong>（{@code Money} が 1 段ずつ丸める）。
 * 基本料金 → 調整 → 割引 → 消費税 → 合計の順で計算する。
 */
public class Invoice {

    private final InvoiceParties parties;
    private final long version;

    /** 料金調整。<strong>無ければ {@code null}</strong>（調整していない事実を表す）。 */
    private Adjustment adjustment;

    /** 丸め後の金額。<strong>ひと組で動く</strong>（片方だけ更新された状態を作らない）。 */
    private InvoiceAmounts amounts;

    private ChargeStatus chargeStatus;

    private Invoice(
            InvoiceParties parties, InvoiceAmounts amounts,
            Adjustment adjustment, ChargeStatus chargeStatus, long version) {
        this.parties = parties;
        this.amounts = amounts;
        this.adjustment = adjustment;
        this.chargeStatus = chargeStatus;
        this.version = version;
    }

    /**
     * 輸送実績から料金を算出する（US21 / US22）。
     *
     * <p><strong>割引の可否は荷主種別で決まる</strong>（{@link DiscountPolicy}）。
     * 個人荷主でも率 0% で同じ道を通す。
     *
     * @param contractRate 契約割引率。<strong>未設定なら {@code null}</strong>
     */
    public static Invoice calculate(
            InvoiceParties parties, Money baseAmount,
            DiscountRate contractRate, BigDecimal taxRate) {
        requireNotNull(parties, "精算書の相手");
        requireNotNull(baseAmount, "基本料金");
        if (taxRate == null || taxRate.signum() < 0) {
            throw new IllegalArgumentException("税率は 0 以上の値が必須です");
        }

        DiscountRate applied = DiscountPolicy.of(parties.shipperId().isCorporate())
                .resolveRate(contractRate);

        Invoice invoice = new Invoice(
                parties,
                new InvoiceAmounts(baseAmount, applied, Money.zeroYen(), taxRate,
                        Money.zeroYen(), baseAmount),
                null, ChargeStatus.DRAFT, 0L);
        invoice.recalculate();
        return invoice;
    }

    /**
     * 料金調整を反映する（US21 の受入基準 6）。
     *
     * <p><strong>確定後は調整できない。</strong> 確定は経理担当者が金額を承認した印であり、
     * 後から動かせるなら確定という操作に意味が無い。
     */
    public void adjust(Adjustment newAdjustment) {
        requireDraft("料金調整");
        requireNotNull(newAdjustment, "料金調整");
        // **計算してから代入する。** 先に代入して巻き戻すと、
        // 巻き戻しに失敗した場合に壊れた状態が残る。
        // 請求額を超える減額は Money が拒み、ここへは到達しない
        InvoiceAmounts recalculated = calculateAmounts(newAdjustment);
        this.adjustment = newAdjustment;
        this.amounts = recalculated;
    }

    /**
     * 料金を確定する（US21）。
     *
     * <p><strong>二度目の確定は拒む。</strong> 確定は取り消せない操作である。
     */
    public void confirmCharge() {
        requireDraft("確定");
        this.chargeStatus = ChargeStatus.CONFIRMED;
    }

    /**
     * 永続化された精算書から復元する。
     *
     * <p><strong>ここで再計算しない。</strong> 保存された金額をそのまま読む。
     * 再計算すると、税率が変わった日に過去の請求書がすべて書き換わる。
     *
     * <p><strong>調整を持たない古い行も読める。</strong> 新しい不変条件で既存の行を
     * 読めなくしない（V22 / V23 / V24 / V26 と同じ判断）。
     */
    public static Invoice reconstruct(
            InvoiceParties parties, InvoiceAmounts amounts,
            Adjustment adjustment, ChargeStatus chargeStatus, long version) {
        return new Invoice(parties, amounts, adjustment, chargeStatus, version);
    }

    /** 金額を計算し直して反映する（<strong>下書きの間だけ</strong>）。 */
    private void recalculate() {
        this.amounts = calculateAmounts(adjustment);
    }

    /**
     * 金額を計算する（<strong>状態を変えない</strong>）。
     *
     * <p>順序: 基本料金 → 調整 → 割引 → 消費税 → 合計。
     * <strong>各段で丸める</strong>（{@code Money} が引き受ける）。
     *
     * <p><strong>計算と代入を分ける。</strong> 代入してから巻き戻す形にすると、
     * 巻き戻しに失敗した場合に壊れた状態が残る。
     */
    private InvoiceAmounts calculateAmounts(Adjustment applied) {
        Money base = amounts.baseAmount();
        DiscountRate rate = amounts.discountRate();
        BigDecimal tax = amounts.taxRate();

        Money adjusted = applied == null ? base : applied.applyTo(base);
        Money discounted = adjusted.multiply(rate.discountFactor());
        Money taxAmount = discounted.multiply(tax);
        return new InvoiceAmounts(
                base, rate, adjusted.subtract(discounted), tax,
                taxAmount, discounted.add(taxAmount));
    }

    private void requireDraft(String operation) {
        if (chargeStatus.isConfirmed()) {
            throw new IllegalStateException(
                    "確定済みの精算書には%sできません".formatted(operation));
        }
    }

    private static void requireNotNull(Object value, String name) {
        if (value == null) {
            throw new IllegalArgumentException(name + "は必須です");
        }
    }

    /** 精算書が指す相手（精算書番号・予約・荷主）。 */
    public InvoiceParties parties() {
        return parties;
    }

    /** 丸め後の金額のひと組。 */
    public InvoiceAmounts amounts() {
        return amounts;
    }

    public InvoiceId invoiceId() {
        return parties.invoiceId();
    }

    public BillingBookingId cargoBookingId() {
        return parties.cargoBookingId();
    }

    public BillingShipperId shipperId() {
        return parties.shipperId();
    }

    public Money baseAmount() {
        return amounts.baseAmount();
    }

    public DiscountRate discountRate() {
        return amounts.discountRate();
    }

    /** 割引額。<strong>US22 の「割引計算の根拠」として保存する。</strong> */
    public Money discountAmount() {
        return amounts.discountAmount();
    }

    /** 料金調整。<strong>していなければ {@code null}</strong>。 */
    public Adjustment adjustment() {
        return adjustment;
    }

    /** 調整があるか。**画面の出し分けは本述語をそのまま呼ぶ。** */
    public boolean hasAdjustment() {
        return adjustment != null;
    }

    /** 税率。<strong>金額だけでは根拠を再現できないため保存する。</strong> */
    public BigDecimal taxRate() {
        return amounts.taxRate();
    }

    public Money taxAmount() {
        return amounts.taxAmount();
    }

    public Money totalAmount() {
        return amounts.totalAmount();
    }

    public ChargeStatus chargeStatus() {
        return chargeStatus;
    }

    /** 確定済みか。**画面の出し分けは本述語をそのまま呼ぶ。** */
    public boolean isConfirmed() {
        return chargeStatus.isConfirmed();
    }

    public long version() {
        return version;
    }
}
