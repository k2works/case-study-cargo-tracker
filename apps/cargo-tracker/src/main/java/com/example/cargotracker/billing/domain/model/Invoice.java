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

    /**
     * 楽観的ロックの版（IT13 レビュー C13）。
     *
     * <p><strong>保存に成功したら進める。</strong> 読み込んだ時点のまま持っていると、
     * 1 回目の保存で DB の版が進み、<strong>同じ集約の 2 回目が必ず競合する</strong>。
     * 他の担当者は誰も触っていないのに「他の担当者が先に更新しました」と出る。
     *
     * <p>競合の検知は<strong>他人の更新を守るため</strong>のものであり、
     * 自分の直前の更新で止まってはならない。
     */
    private long version;

    /** 料金調整。<strong>無ければ {@code null}</strong>（調整していない事実を表す）。 */
    private Adjustment adjustment;

    /** 丸め後の金額。<strong>ひと組で動く</strong>（片方だけ更新された状態を作らない）。 */
    private InvoiceAmounts amounts;

    private ChargeStatus chargeStatus;

    /**
     * 精算書の発行（US23）。<strong>発行前は {@code null}</strong>。
     *
     * <p>支払期限は発行時に決めて保持する。都度計算すると、設定を変えた日に
     * 既発行分の期限が動く。
     */
    private Issuance issuance;

    /** 入金の記録（US23）。<strong>入金確認前は {@code null}</strong>。 */
    private Payment payment;

    /**
     * 支払いの状態（US23）。
     *
     * <p><strong>フィールドが常に値を持つのは内部の都合である</strong>
     * （未発行なら {@code PENDING}）。集約の中で null 検査を撒かないためであり、
     * <strong>外向きには未発行を {@code null} で表す</strong>（{@link #paymentStatus()}）
     * — 発行していない請求書に「未入金」と出すと、督促の対象に見えるためである。
     */
    private PaymentStatus paymentStatus = PaymentStatus.PENDING;

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
        requireNotNull(parties, "請求書の相手");
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
     * 精算書を発行する（US23 の受入基準 1・2）。
     *
     * <p><strong>発行できるのは確定済みだけである。</strong> 確定は経理担当者が
     * 金額を承認した印であり、承認前の金額で請求書を出すと、
     * 荷主に伝えた後で金額が変わる。
     *
     * <p><strong>二度は発行しない。</strong> 同じ請求書を 2 通送ることになり、
     * 荷主はどちらが本物か分からない。
     *
     * @throws IllegalStateException 確定前、または発行済みのとき
     */
    public void issue(Issuance newIssuance) {
        requireNotNull(newIssuance, "発行の内容");
        if (!chargeStatus.isConfirmed()) {
            throw new IllegalStateException("確定していない請求書は発行できません");
        }
        if (isIssued()) {
            throw new IllegalStateException("すでに発行済みの請求書です");
        }
        this.issuance = newIssuance;
        this.paymentStatus = PaymentStatus.PENDING;
    }

    /**
     * 入金を確認する（US23 の受入基準 4）。
     *
     * <p><strong>請求額どおりの入金だけを受ける</strong>（一部入金・過入金は拒む）。
     * 差額の扱いは業務であり（不足は再請求、過入金は返金）、
     * <strong>システムが黙って受けると帳簿と合わなくなる</strong>。
     *
     * <p><strong>遅れても入金は入金である。</strong> 期限超過の請求書でも確認できる。
     *
     * @throws IllegalStateException    発行前、または入金確認済みのとき
     * @throws IllegalArgumentException 入金額が請求額と違うとき
     */
    public void confirmPayment(Payment newPayment) {
        requireNotNull(newPayment, "入金の記録");
        if (!isIssued()) {
            throw new IllegalStateException("発行していない請求書に入金は確認できません");
        }
        if (paymentStatus.isPaid()) {
            throw new IllegalStateException("すでに入金を確認済みの請求書です");
        }
        if (newPayment.paidAmount().value().compareTo(totalAmount().value()) != 0) {
            throw new IllegalArgumentException(
                    "入金額が請求額と一致しません（請求額 %s 円）"
                            .formatted(totalAmount().value()));
        }
        this.payment = newPayment;
        this.paymentStatus = PaymentStatus.CONFIRMED;
    }

    /**
     * 支払期限の超過を反映する（US23 の受入基準 5）。
     *
     * <p><strong>画面を開いたときに判定する。</strong> 夜間バッチにすると、
     * 動いているかを誰も確かめない。
     *
     * <p><strong>入金済みは戻さない。</strong> 判定を毎回走らせるため、
     * 入金確認の後に開いた画面で督促対象へ戻る形を作らない。
     * <strong>発行前は何もしない</strong> — まだ期限そのものが無い。
     *
     * @param today 業務のタイムゾーンでの今日
     * @return 状態が変わったなら {@code true}
     */
    public boolean markOverdue(java.time.LocalDate today) {
        // **null 検査をここに書く。** 述語に隠すと、静的解析が
        // 「初期化されないフィールドを検査なしで読んでいる」と読む
        if (issuance == null || !paymentStatus.awaitingPayment()) {
            return false;
        }
        if (!issuance.isOverdue(today) || paymentStatus.isOverdue()) {
            return false;
        }
        this.paymentStatus = PaymentStatus.OVERDUE;
        return true;
    }

    /** 発行済みか。<strong>画面の出し分けは本述語をそのまま呼ぶ。</strong> */
    public boolean isIssued() {
        return issuance != null;
    }

    /** 発行の内容（発行日時・支払期限）。<strong>発行前は {@code null}</strong>。 */
    public Issuance issuance() {
        return issuance;
    }

    /** 入金の記録。<strong>入金確認前は {@code null}</strong>。 */
    public Payment payment() {
        return payment;
    }

    /**
     * 支払いの状態。<strong>発行前は {@code null}</strong>。
     *
     * <p>発行していない請求書に「未入金」と出すと、督促の対象に見える。
     */
    public PaymentStatus paymentStatus() {
        return isIssued() ? paymentStatus : null;
    }

    /**
     * 発行と入金の状態を載せて復元する（US23）。
     *
     * <p><strong>復元の引数を増やさない</strong>（{@code Cargo.withClaimCode} と同じ形）。
     * <strong>発行前の請求書も復元できる</strong> — 列が無かったころの行を拒むと、
     * その請求書の画面ごと開けなくなる。
     */
    public Invoice withSettlement(
            Issuance restoredIssuance, PaymentStatus status, Payment restoredPayment) {
        this.issuance = restoredIssuance;
        this.paymentStatus = restoredIssuance == null || status == null
                ? PaymentStatus.PENDING : status;
        this.payment = restoredPayment;
        return this;
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
                    "確定済みの請求書には%sできません".formatted(operation));
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

    /**
     * 法人荷主への請求か（IT13 レビュー C6）。
     *
     * <p><strong>割引率から逆算しない。</strong> 契約はあるが割引条件がまだ
     * 登録されていない法人は率が 0% であり、逆算すると個人になる。
     * <strong>0% は「法人でない」ではない。</strong>
     */
    public boolean corporate() {
        return parties.shipperId().isCorporate();
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

    /**
     * 保存に成功したことを反映する（C13）。
     *
     * <p><strong>呼ぶのはリポジトリだけである。</strong> 更新が 1 件だった
     * ことを知っているのはそこであり、集約は自分が保存されたかを知らない。
     */
    public void markPersisted() {
        this.version++;
    }
}
