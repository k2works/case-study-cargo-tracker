package com.example.cargotracker.billing.infrastructure.query;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/** 請求の読み取りモデル（domain-model.md「クエリ一覧」）。 */
public final class BillingQueries {

    private BillingQueries() {
    }

    /**
     * 請求一覧（S60 / US21）。
     *
     * @param includeSettled 入金済・取消も出すか。<b>既定では外す</b>——決着した
     *     ものが混ざると、一覧全体が「まだ手を入れる場所」に見えなくなる
     * @param bookingId 予約で絞る（予約詳細から請求書へ飛ぶときに使う）
     */
    public record FindInvoicesQuery(
            boolean includeSettled,
            String bookingId,
            String shipperId,
            java.time.LocalDate calculatedFrom,
            java.time.LocalDate calculatedTo) {
    }

    /** 請求書 1 通（S61）。 */
    public record FindInvoiceQuery(String invoiceId) {
    }

    /** その予約の有効な請求書（予約詳細から飛ぶ・二重作成の確認）。 */
    public record FindInvoiceOfBookingQuery(String bookingId) {
    }

    /**
     * 未払いの請求書（S60 の絞り込み・US23 §受入基準 5）。
     *
     * <p><b>期限超過は列に持たない</b>（不変条件 4）。問い合わせるたびに
     * {@code due_on &lt; today} で数える。<b>期限当日は超過ではない。</b></p>
     *
     * @param today <b>業務タイムゾーンの今日</b>。呼ぶ側が決めて渡す——DB の
     *     {@code CURRENT_DATE} を使うと、サーバのタイムゾーンで判断される
     */
    public record FindOverdueInvoicesQuery(java.time.LocalDate today) {
    }

    /**
     * 荷主が読む自社の請求書（S62 / US23 §受入基準 2）。
     *
     * <p><b>荷主 ID で絞る。</b> 他社の請求書は読めない——金額を出す唯一の
     * 荷主向け画面なので、絞りを画面に任せない。</p>
     *
     * <p><b>発行済だけを出す。</b> 算出済は社内の途中経過で、荷主に見せる
     * ものではない。取消も出さない（一度取り消したものを見せ続けない）。</p>
     */
    public record FindShipperInvoiceQuery(String invoiceId, String shipperId) {
    }

    /**
     * 荷主が予約から引く自社の請求書（S62 / US23 §受入基準 2）。
     *
     * <p><b>荷主は請求書番号を知らない。</b> 荷主が持っているのは予約番号と
     * 追跡番号で、番号を打たせると探しに行くことになる。経理向けの
     * {@link FindInvoiceOfBookingQuery} と同じ形にする。</p>
     */
    public record FindShipperInvoiceOfBookingQuery(String bookingId, String shipperId) {
    }

    /**
     * 請求一覧（S60）。
     *
     * @param totalAmount 絞り込んだぶんの<b>合計金額</b>（IT13 引き継ぎ D。締めの仕事）。
     *     <b>サーバが数える</b>——画面で足すと、一覧の上限で切れたぶんが静かに
     *     合計から落ちる
     */
    public record InvoiceListView(List<InvoiceSummaryView> items, int total,
            BigDecimal totalAmount) {
    }

    /**
     * 一覧の 1 行（S60）。
     *
     * @param statusLabel 画面に出す呼び名。<b>列挙名を出さない</b>
     */
    public record InvoiceSummaryView(
            String invoiceId,
            String bookingId,
            String shipperId,
            String shipperName,
            String shipperTypeLabel,
            String status,
            String statusLabel,
            BigDecimal totalAmount,
            String currency,
            Instant calculatedAt,
            java.time.LocalDate dueOn,
            boolean overdue) {
    }

    /**
     * 請求書 1 通の詳細（S61）。
     *
     * <p><b>明細も一緒に返す。</b> 行ごとに問い合わせると N+1 になり、
     * 何より「金額の根拠」は 1 画面で読めなければ根拠にならない。</p>
     *
     * @param quotedAmount 見積時の概算。<b>見積を経ない予約では {@code null}</b>
     *     （注 N12）。S61 は両方の場合を出し分ける（概算行と差額を出さない）
     * @param issuedOn 発行日。<b>未発行なら {@code null}</b>
     * @param dueOn 支払期限（発行日 + 30 日・不変条件 3）。<b>未発行なら {@code null}</b>
     * @param overdue 支払期限を過ぎているか。<b>列ではなく問い合わせのたびに数える</b>
     *     （不変条件 4）。期限当日は超過ではない
     */
    public record InvoiceView(
            String invoiceId,
            String bookingId,
            String shipperId,
            String shipperName,
            String shipperType,
            String shipperTypeLabel,
            String contractNumber,
            BigDecimal discountRate,
            BigDecimal baseAmount,
            BigDecimal discountAmount,
            BigDecimal adjustmentAmount,
            BigDecimal taxAmount,
            BigDecimal totalAmount,
            String currency,
            String status,
            String statusLabel,
            Instant calculatedAt,
            BigDecimal quotedAmount,
            java.time.LocalDate issuedOn,
            java.time.LocalDate dueOn,
            Instant paidAt,
            boolean overdue,
            List<InvoiceLineView> lineItems,
            List<PaymentView> payments) {
    }

    /**
     * 入金の 1 行（S61）。
     *
     * <p><b>取り消した入金も出す。</b> 行を消さないのは「誤って記録して取り消した」
     * 事実を残すためで、出さなければ残した意味が無い。</p>
     *
     * @param voidedAt 取り消した日時。<b>入っていれば入金として数えない</b>
     */
    public record PaymentView(
            String paymentId,
            BigDecimal amount,
            String currency,
            Instant paidAt,
            String recordedBy,
            Instant voidedAt,
            String voidedBy,
            String voidReason) {
    }

    /**
     * 明細の 1 行（S61）。
     *
     * @param basisExceptionId 調整の根拠になった例外 ID。S61 から例外へ飛ぶ
     *     （US28 §8「誤配の事実は料金調整の根拠として参照できる」の受け側）
     * @param adjustmentId 調整の識別子。<b>取り消す操作の宛先</b>（IT14 引き継ぎ C）
     * @param reversed すでに取り消されたか。<b>取り消し済みに取り消しを出さない</b>
     *     ——押せるのに断られる操作を並べない
     */
    public record InvoiceLineView(
            String itemType,
            String itemTypeLabel,
            String description,
            BigDecimal amount,
            String currency,
            String basisExceptionId,
            String adjustmentId,
            boolean reversed) {
    }
}
