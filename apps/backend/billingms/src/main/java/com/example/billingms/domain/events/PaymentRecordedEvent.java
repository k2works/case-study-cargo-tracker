package com.example.billingms.domain.events;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 入金記録済イベント（US23、domain-model.md L984）。
 *
 * <p>Invoice 集約が {@code INVOICED|OVERDUE → PAID} に遷移したことを表す。集約発火型（ADR-0012）で
 * Read Model 投影で {@code paid_at} カラムと {@code payment} テーブルへの履歴 INSERT を実施し、
 * cross-service では bookingms の {@code Cargo} 集約を {@code SETTLED} 状態に遷移させる（T4.5）。</p>
 *
 * @param invoiceId         Invoice 識別子
 * @param paymentId         入金識別子
 * @param bookingId         予約識別子（bookingms cross-service の関連付けに使用、T4.5）
 * @param shipperId         荷主識別子
 * @param paidAmount        入金額
 * @param currency          通貨コード（ISO 4217 3 文字）
 * @param paidAt            入金時刻
 * @param paymentMethod     支払方法（null 可）
 * @param externalReference 決済機関の取引番号（null 可）
 * @param recordedAt        記録時刻（Clock 注入で決定的）
 */
public record PaymentRecordedEvent(
        String invoiceId,
        String paymentId,
        String bookingId,
        String shipperId,
        BigDecimal paidAmount,
        String currency,
        LocalDateTime paidAt,
        String paymentMethod,
        String externalReference,
        LocalDateTime recordedAt
) {
}
