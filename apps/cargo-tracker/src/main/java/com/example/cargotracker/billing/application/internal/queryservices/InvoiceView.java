package com.example.cargotracker.billing.application.internal.queryservices;

import com.example.cargotracker.billing.domain.model.valueobjects.InvoiceType;
import java.math.BigDecimal;

/**
 * 精算書 1 件の表示用（US21 / US22）。
 *
 * <p><strong>US22 の「割引計算の根拠」を画面に出すための形である。</strong>
 * 割引率・基本料金・割引後料金の 3 つがそろって初めて根拠になる。
 *
 * <p><strong>意味のまとまりごとに分ける</strong>（IT14 の M9 / IT15 の M5。C3）。
 * 旧版は 27 個の値を一列に並べており、<strong>同型の引数を取り違えても
 * コンパイルが通る</strong>状態だった。並びが長いほど、足すときに
 * 「どこへ足すか」を考えなくなる — 実際に IT13 から IT16 のあいだに
 * 21 個から 27 個まで増えた。
 *
 * <p><strong>Checkstyle の「引数は 7 個まで」はレコードを見ない。</strong>
 * 規則は宣言されているのに、対象が検査の視野の外にあった。
 * {@code RecordComponentCountTest} が代わりに数える。
 *
 * <p><strong>画面が呼ぶ名前は変えない。</strong> まとまりに分けたのは
 * 組み立て側の都合であり、読む側にその都合を押し付けない
 * （委譲するアクセサを残す）。
 *
 * @param identity 誰への何の請求か
 * @param amounts  金額の内訳
 * @param adjustment 料金調整。<strong>調整が無くても値は入る</strong>（額は 0）
 * @param charge   料金の状態と種別
 * @param settlement 発行と支払いの状態
 * @param corporate 法人荷主への請求か（IT13 レビュー C6）。
 *                  <strong>割引率から逆算しない</strong> — 契約はあるが
 *                  割引条件が未登録の法人は率 0% であり、逆算すると個人になる
 */
public record InvoiceView(
        Identity identity,
        Amounts amounts,
        AdjustmentDetail adjustment,
        ChargeState charge,
        SettlementState settlement,
        boolean corporate) {

    /**
     * 誰への何の請求か。
     *
     * @param invoiceNumber  精算書番号
     * @param bookingId      予約 ID
     * @param trackingNumber 追跡番号。<strong>経理担当者が貨物を指す値である</strong>
     * @param shipperName    荷主名（宛名。<strong>発行時点で凍結する</strong>）
     * @param shipperId      荷主 ID（IT14 レビュー C3）。<strong>連絡先を引くための鍵であり、
     *                       画面には出さない。</strong> 連絡先そのものを持たせない理由は、
     *                       宛名（凍結）と違い<strong>いま届く先</strong>だからである
     */
    public record Identity(
            String invoiceNumber,
            String bookingId,
            String trackingNumber,
            String shipperName,
            String shipperId) {
    }

    /**
     * 金額の内訳。
     *
     * <p><strong>丸め後の値を運ぶ。</strong> 画面で計算し直すと、
     * 段階丸めの結果と食い違う（`domain-model.md`）。
     *
     * @param baseAmount      基本料金（割引適用前）
     * @param discountPercent 適用した割引率（百分率）
     * @param discountAmount  割引額
     * @param taxPercent      消費税率（百分率）
     * @param taxAmount       消費税額
     * @param totalAmount     請求総額
     */
    public record Amounts(
            BigDecimal baseAmount,
            BigDecimal discountPercent,
            BigDecimal discountAmount,
            BigDecimal taxPercent,
            BigDecimal taxAmount,
            BigDecimal totalAmount) {
    }

    /**
     * 料金調整（ADR-016）。
     *
     * @param reason       調整の理由。<strong>調整が無ければ {@code null}</strong>
     * @param reduction    減額
     * @param compensation 補償費用
     */
    public record AdjustmentDetail(
            String reason,
            BigDecimal reduction,
            BigDecimal compensation) {
    }

    /**
     * 料金の状態と種別（ADR-017 / ADR-020）。
     *
     * <p><strong>支払いの軸と混ぜない。</strong> 料金が確定していることと、
     * 支払いが済んでいることは別である。
     *
     * @param statusLabel 表示名
     * @param statusBadge バッジ（正典は {@code ChargeStatus}）
     * @param confirmed   確定済みか
     * @param invoiceType 請求書の種別（ADR-020。IT15 レビュー M8）。
     *                    <strong>輸送料金とキャンセル料が並んだときに区別がつかないと、
     *                    経理担当者はどちらを督促しているのか分からなくなる</strong>
     */
    public record ChargeState(
            String statusLabel,
            String statusBadge,
            boolean confirmed,
            InvoiceType invoiceType) {
    }

    /**
     * 発行と支払いの状態（US23）。
     *
     * @param deadline    発行日・支払期限・超過日数
     * @param statusLabel 支払いの状態の表示名。<strong>未発行なら {@code null}</strong>
     * @param statusBadge 支払いの状態のバッジ（正典は {@code PaymentStatus}）
     * @param issued      発行済みか
     * @param paid        入金確認済みか
     * @param payment     入金の記録。<strong>未入金なら {@code null}</strong>
     */
    public record SettlementState(
            Deadline deadline,
            String statusLabel,
            String statusBadge,
            boolean issued,
            boolean paid,
            PaymentDetail payment) {
    }

    /**
     * 発行と期限（US23 / ADR-019）。
     *
     * <p><strong>3 つはひと組で動く。</strong> 発行して初めて期限が決まり、
     * 期限を過ぎて初めて日数が付く。
     *
     * @param issuedAt    発行日。<strong>未発行なら {@code null}</strong>
     * @param dueDate     支払期限。<strong>未発行なら {@code null}</strong>
     * @param daysOverdue 支払期限を過ぎた日数（<strong>過ぎていなければ 0</strong>）
     */
    public record Deadline(
            java.time.LocalDate issuedAt,
            java.time.LocalDate dueDate,
            long daysOverdue) {
    }

    // --- 画面が呼ぶ名前（まとまりに分けた都合を読む側に押し付けない） ---

    public String invoiceNumber() {
        return identity.invoiceNumber();
    }

    public String bookingId() {
        return identity.bookingId();
    }

    public String trackingNumber() {
        return identity.trackingNumber();
    }

    public String shipperName() {
        return identity.shipperName();
    }

    public String shipperId() {
        return identity.shipperId();
    }

    public BigDecimal baseAmount() {
        return amounts.baseAmount();
    }

    public BigDecimal discountPercent() {
        return amounts.discountPercent();
    }

    public BigDecimal discountAmount() {
        return amounts.discountAmount();
    }

    public BigDecimal taxPercent() {
        return amounts.taxPercent();
    }

    public BigDecimal taxAmount() {
        return amounts.taxAmount();
    }

    public BigDecimal totalAmount() {
        return amounts.totalAmount();
    }

    public String adjustmentReason() {
        return adjustment.reason();
    }

    public BigDecimal reduction() {
        return adjustment.reduction();
    }

    public BigDecimal compensation() {
        return adjustment.compensation();
    }

    public String chargeStatusLabel() {
        return charge.statusLabel();
    }

    public String chargeStatusBadge() {
        return charge.statusBadge();
    }

    public boolean confirmed() {
        return charge.confirmed();
    }

    public java.time.LocalDate issuedAt() {
        return settlement.deadline().issuedAt();
    }

    public java.time.LocalDate dueDate() {
        return settlement.deadline().dueDate();
    }

    public String paymentStatusLabel() {
        return settlement.statusLabel();
    }

    public String paymentStatusBadge() {
        return settlement.statusBadge();
    }

    public boolean issued() {
        return settlement.issued();
    }

    public boolean paid() {
        return settlement.paid();
    }

    public PaymentDetail payment() {
        return settlement.payment();
    }

    public long daysOverdue() {
        return settlement.deadline().daysOverdue();
    }

    /** 種別の表示名（{@code InvoiceType} が正典）。 */
    public String invoiceTypeLabel() {
        return charge.invoiceType() == null ? "" : charge.invoiceType().displayName();
    }

    /** 種別のバッジ（{@code InvoiceType} が正典）。 */
    public String invoiceTypeBadge() {
        return charge.invoiceType() == null ? "" : charge.invoiceType().badgeClass();
    }

    /**
     * 発行できるか（US23）。
     *
     * <p><strong>画面の出し分けは本述語をそのまま呼ぶ。</strong> 確定済みで
     * まだ発行していない請求書だけが発行の入口を持つ。
     */
    public boolean canIssue() {
        return confirmed() && !issued();
    }

    /**
     * 入金を確認できるか（US23）。
     *
     * <p><strong>遅れても入金は入金である。</strong> 期限超過でも確認できる。
     */
    public boolean canConfirmPayment() {
        return issued() && !paid();
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
        return daysOverdue() > 0;
    }

    /** 割引が適用されているか。**画面の出し分けは本述語をそのまま呼ぶ。** */
    public boolean hasDiscount() {
        return discountPercent() != null && discountPercent().signum() > 0;
    }

    /** 料金調整があるか。 */
    public boolean hasAdjustment() {
        return adjustmentReason() != null && !adjustmentReason().isBlank();
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
        return totalAmount().subtract(taxAmount());
    }
}
