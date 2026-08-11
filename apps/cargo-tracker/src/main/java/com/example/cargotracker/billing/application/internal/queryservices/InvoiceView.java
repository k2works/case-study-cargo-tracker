package com.example.cargotracker.billing.application.internal.queryservices;

import com.example.cargotracker.billing.domain.model.InvoiceType;
import java.math.BigDecimal;

/**
 * 精算書 1 件の表示用（US21 / US22）。
 *
 * <p><strong>US22 の「割引計算の根拠」を画面に出すための形である。</strong>
 * 割引率・基本料金・割引後料金の 3 つがそろって初めて根拠になる。
 *
 * @param invoiceNumber    精算書番号
 * @param bookingId        予約 ID
 * @param trackingNumber   追跡番号。<strong>経理担当者が貨物を指す値である</strong>
 * @param shipperName      荷主名
 * @param baseAmount       基本料金（割引適用前）
 * @param discountPercent  適用した割引率（百分率）
 * @param discountAmount   割引額
 * @param adjustmentReason 料金調整の理由。<strong>調整が無ければ {@code null}</strong>
 * @param reduction        減額
 * @param compensation     補償費用
 * @param taxPercent       消費税率（百分率）
 * @param taxAmount        消費税額
 * @param totalAmount      請求総額
 * @param chargeStatusLabel 料金の状態の表示名
 * @param chargeStatusBadge 料金の状態のバッジ（正典は {@code ChargeStatus}）
 * @param confirmed        確定済みか
 * @param issuedAt         発行日（US23）。<strong>未発行なら {@code null}</strong>
 * @param dueDate          支払期限（US23）。<strong>未発行なら {@code null}</strong>
 * @param paymentStatusLabel 支払いの状態の表示名。<strong>未発行なら {@code null}</strong>
 * @param paymentStatusBadge 支払いの状態のバッジ（正典は {@code PaymentStatus}）
 * @param issued           発行済みか
 * @param paid             入金確認済みか
 * @param payment          入金の記録（US23）。<strong>未入金なら {@code null}</strong>
 * @param daysOverdue      支払期限を過ぎた日数（<strong>過ぎていなければ 0</strong>）。
 *                         督促の強さはこの日数で変わる
 * @param corporate        法人荷主への請求か（IT13 レビュー C6）。
 *                         <strong>割引率から逆算しない</strong> — 契約はあるが
 *                         割引条件が未登録の法人は率 0% であり、逆算すると個人になる
 */
public record InvoiceView(
        String invoiceNumber,
        String bookingId,
        String trackingNumber,
        String shipperName,
        /* 荷主 ID（IT14 レビュー C3）。**連絡先を引くための鍵であり、画面には出さない。**
           連絡先そのものを持たせない理由は、宛名（凍結）と違い
           **いま届く先**だからである — 写すと変更に追随しない。 */
        String shipperId,
        BigDecimal baseAmount,
        BigDecimal discountPercent,
        BigDecimal discountAmount,
        String adjustmentReason,
        BigDecimal reduction,
        BigDecimal compensation,
        BigDecimal taxPercent,
        BigDecimal taxAmount,
        BigDecimal totalAmount,
        String chargeStatusLabel,
        String chargeStatusBadge,
        boolean confirmed,
        java.time.LocalDate issuedAt,
        java.time.LocalDate dueDate,
        String paymentStatusLabel,
        String paymentStatusBadge,
        boolean issued,
        boolean paid,
        PaymentDetail payment,
        long daysOverdue,
        boolean corporate,
        /* 請求書の種別（ADR-020。IT15 レビュー M8）。**輸送料金とキャンセル料が
           並んだときに区別がつかないと、経理担当者はどちらを督促しているのか
           分からなくなる。** 表示名とバッジの正典は InvoiceType が持つ —
           画面に対応表を書き写さない */
        InvoiceType invoiceType) {

    /** 種別の表示名（{@code InvoiceType} が正典）。 */
    public String invoiceTypeLabel() {
        return invoiceType == null ? "" : invoiceType.displayName();
    }

    /** 種別のバッジ（{@code InvoiceType} が正典）。 */
    public String invoiceTypeBadge() {
        return invoiceType == null ? "" : invoiceType.badgeClass();
    }

    /**
     * 発行できるか（US23）。
     *
     * <p><strong>画面の出し分けは本述語をそのまま呼ぶ。</strong> 確定済みで
     * まだ発行していない請求書だけが発行の入口を持つ。
     */
    public boolean canIssue() {
        return confirmed && !issued;
    }

    /**
     * 入金を確認できるか（US23）。
     *
     * <p><strong>遅れても入金は入金である。</strong> 期限超過でも確認できる。
     */
    public boolean canConfirmPayment() {
        return issued && !paid;
    }

    /**
     * 入金の記録（表示用）。
     *
     * <p><strong>ひと組で動く値をひと組で運ぶ。</strong> 同型の引数を並べると、
     * 位置を取り違えてもコンパイルが通る（{@code InvoiceParties} と同じ判断）。
     *
     * @param amount      入金額
     * @param at          入金日時（業務のタイムゾーン）
     * @param methodLabel 支払方法の表示名
     * @param reference   取引の参照番号。<strong>無い入金もある</strong>（窓口振込など）
     */
    public record PaymentDetail(
            java.math.BigDecimal amount,
            java.time.LocalDateTime at,
            String methodLabel,
            String reference) {

        /** 参照番号があるか。<strong>画面の出し分けは本述語をそのまま呼ぶ。</strong> */
        public boolean hasReference() {
            return reference != null && !reference.isBlank();
        }
    }

    /** 支払期限を過ぎているか。<strong>日数の計算は集約が行う。</strong> */
    public boolean overdue() {
        return daysOverdue > 0;
    }

    /** 割引が適用されているか。**画面の出し分けは本述語をそのまま呼ぶ。** */
    public boolean hasDiscount() {
        return discountPercent != null && discountPercent.signum() > 0;
    }

    /** 料金調整があるか。 */
    public boolean hasAdjustment() {
        return adjustmentReason != null && !adjustmentReason.isBlank();
    }

    /**
     * 割引後料金（消費税を計算した対象）。
     *
     * <p><strong>「基本料金 − 割引額」で求めてはならない</strong>（レビュー H1）。
     * 計算の順序は<strong>基本料金 → 料金調整 → 割引 → 消費税</strong>であり、
     * 割引は<strong>調整後の額</strong>に掛かる。基本料金から引くと、
     * 調整がある請求書で<strong>画面の内訳が足し算として成立しなくなる</strong>
     * （経理担当者が電卓で検算する場面で最初に見つかる種類の食い違いである）。
     *
     * <p><strong>丸め後の値どうしで導く。</strong> 請求総額から消費税を引けば、
     * 集約が計算した割引後料金と必ず一致する（どちらも段階丸めの結果である）。
     */
    public BigDecimal discountedAmount() {
        return totalAmount.subtract(taxAmount);
    }
}
