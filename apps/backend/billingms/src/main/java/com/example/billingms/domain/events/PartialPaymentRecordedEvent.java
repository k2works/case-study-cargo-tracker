package com.example.billingms.domain.events;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 部分入金記録イベント（IT9 A1.4 / ADR-0020 / US26）。
 *
 * <p>Invoice 集約が部分入金を受理した際に発火する billingms 内部 event。
 * cross-service 契約には含めない（部分入金時点で Cargo は SETTLED にならないため）。
 * 残額がゼロになった時点で shared {@code PaymentRecordedEvent} を別途発火し、
 * bookingms cross-service が Cargo を SETTLED に遷移させる。</p>
 *
 * <p>billingms 内 {@code InvoiceProjection} の {@code @EventHandler} が本 event を購読し、
 * payment テーブルへの INSERT（{@code is_partial=TRUE}）と invoice テーブルの
 * {@code paid_so_far} 更新および {@code billing_status=PARTIALLY_PAID} 遷移を担う。</p>
 *
 * @param invoiceId    Invoice 識別子
 * @param paymentId    Payment 識別子（payment_method/external_reference の関連付け用）
 * @param bookingId    予約識別子
 * @param shipperId    荷主識別子
 * @param paidAmount   今回の入金額
 * @param currency     通貨コード
 * @param paidAt       入金時刻
 * @param newPaidSoFar 累積入金額（apply 後の状態）
 * @param totalDue     請求総額
 * @param occurredAt   イベント発火時刻
 */
@SuppressWarnings("java:S107") // domain event の属性として正当
public record PartialPaymentRecordedEvent(
        String invoiceId,
        String paymentId,
        String bookingId,
        String shipperId,
        BigDecimal paidAmount,
        String currency,
        LocalDateTime paidAt,
        BigDecimal newPaidSoFar,
        BigDecimal totalDue,
        LocalDateTime occurredAt
) {
}
