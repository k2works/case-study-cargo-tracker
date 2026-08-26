package com.example.billingms.interfaces.rest;

import com.example.billingms.domain.model.Invoice;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/**
 * 発行済みの精算書（US21-5・US22-4）。
 *
 * <p><strong>割引率を返す</strong>（22-4）。額だけでは率を復元できない——基本料金と
 * 割引額から割り戻すと、丸めの分だけずれる。
 *
 * @param invoiceId 請求番号
 * @param invoiceNumber 請求番号（画面が使う名前）
 * @param bookingId 予約番号
 * @param shipperName 発行した時点の荷主の社名
 * @param basis 基本料金の根拠
 * @param baseAmount 基本料金
 * @param discountRate 割引率。割引が無ければ {@code null}
 * @param discountAmount 割引額
 * @param lineItems 調整の明細
 * @param cancellationFee キャンセル料
 * @param taxRate 税率
 * @param taxExempt 輸出免税か。**税区分として画面に出す**——「消費税 ¥0」だけでは
 *        計算漏れと読める（[ADR-027] 決定 8 の改訂）
 * @param taxAmount 消費税
 * @param totalAmount 合計
 * @param paymentStatus 支払いの状態
 * @param issuedAt 発行日時
 * @param dueDate 支払期限（US23 で使う）
 */
public record InvoiceResponse(
        String invoiceId,
        String invoiceNumber,
        String bookingId,
        String shipperName,
        ChargeBasisResponse basis,
        MoneyResponse baseAmount,
        BigDecimal discountRate,
        MoneyResponse discountAmount,
        List<LineItemResponse> lineItems,
        ChargeCalculationResponse.CancellationFeeResponse cancellationFee,
        BigDecimal taxRate,
        boolean taxExempt,
        MoneyResponse taxAmount,
        MoneyResponse totalAmount,
        String paymentStatus,
        String paymentStatusLabel,
        Instant issuedAt,
        LocalDate dueDate,
        PaymentResponse payment,
        Instant voidedAt,
        String voidReason) {

    /**
     * 入金の記録（受入基準 23-3）。
     *
     * <p><strong>根拠ごと返す。</strong>「入金済」だけでは、いつ・いくら・どの振込かを
     * 誰も追えない。
     */
    public record PaymentResponse(MoneyResponse amount, LocalDate paidAt, String method,
            String methodLabel, String transactionReference) {
    }

    /** 調整の明細 1 行（決定 6）。**内容つきで残す**——金額だけでは理由が分からない。 */
    public record LineItemResponse(String description, MoneyResponse amount) {
    }

    private static final java.util.Map<String, String> STATUS_LABELS = java.util.Map.of(
            "PRELIMINARY", "仮受付",
            "ROUTE_PROPOSED", "経路提案中",
            "ROUTE_NOTIFIED", "荷主へ通知済",
            "CONFIRMED", "確定済",
            "TRACKING_ISSUED", "追跡番号発行済",
            "IN_TRANSIT", "輸送中");

    public static InvoiceResponse from(Invoice invoice) {
        return new InvoiceResponse(
                invoice.invoiceId().value(),
                invoice.invoiceId().value(),
                invoice.cargoBookingId().value(),
                invoice.shipperName(),
                ChargeBasisResponse.from(invoice.charge()),
                MoneyResponse.from(invoice.baseAmount()),
                invoice.discountRate() == null ? null : invoice.discountRate().value(),
                invoice.discountRate() == null ? null
                        : MoneyResponse.from(invoice.discountAmount()),
                invoice.lineItems().stream()
                        .map(item -> new LineItemResponse(item.description(),
                                MoneyResponse.from(item.amount())))
                        .toList(),
                invoice.cancellationFee() == null ? null
                        : new ChargeCalculationResponse.CancellationFeeResponse(
                                invoice.cancellationFee().bookingStatusAtCancel().name(),
                                STATUS_LABELS.getOrDefault(
                                        invoice.cancellationFee().bookingStatusAtCancel().name(),
                                        invoice.cancellationFee().bookingStatusAtCancel().name()),
                                invoice.cancellationFee().feeRate(),
                                MoneyResponse.from(invoice.cancellationFee().amount())),
                invoice.taxRate().value(),
                invoice.taxRate().exempted(),
                MoneyResponse.from(invoice.taxAmount()),
                MoneyResponse.from(invoice.totalAmount()),
                invoice.paymentStatus().name(),
                invoice.paymentStatus().label(),
                invoice.issuedAt(),
                invoice.dueDate(),
                invoice.payment() == null ? null : new PaymentResponse(
                        MoneyResponse.from(invoice.payment().amount()),
                        invoice.payment().paidAt(),
                        invoice.payment().method().name(),
                        invoice.payment().method().label(),
                        invoice.payment().transactionReference()),
                invoice.voidedAt(),
                invoice.voidReason());
    }
}
