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
    public record FindInvoicesQuery(boolean includeSettled, String bookingId) {
    }

    /** 請求書 1 通（S61）。 */
    public record FindInvoiceQuery(String invoiceId) {
    }

    /** その予約の有効な請求書（予約詳細から飛ぶ・二重作成の確認）。 */
    public record FindInvoiceOfBookingQuery(String bookingId) {
    }

    /** 一覧の応答。 */
    public record InvoiceListView(List<InvoiceSummaryView> items, int total) {
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
            Instant calculatedAt) {
    }

    /**
     * 請求書 1 通の詳細（S61）。
     *
     * <p><b>明細も一緒に返す。</b> 行ごとに問い合わせると N+1 になり、
     * 何より「金額の根拠」は 1 画面で読めなければ根拠にならない。</p>
     *
     * @param quotedAmount 見積時の概算。<b>本 IT では常に {@code null}</b>——見積は
     *     US01（IT14）なので、見積を経ない予約しかない。S61 は概算行と差額を出さない
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
            List<InvoiceLineView> lineItems) {
    }

    /**
     * 明細の 1 行（S61）。
     *
     * @param basisExceptionId 調整の根拠になった例外 ID。S61 から例外へ飛ぶ
     *     （US28 §8「誤配の事実は料金調整の根拠として参照できる」の受け側）
     */
    public record InvoiceLineView(
            String itemType,
            String itemTypeLabel,
            String description,
            BigDecimal amount,
            String currency,
            String basisExceptionId) {
    }
}
