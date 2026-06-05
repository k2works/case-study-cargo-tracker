package com.example.shared.events;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 入金記録イベント（US23、cross-service / ADR-0009 / ADR-0015）。
 *
 * <p>billingms の {@code Invoice} 集約が {@code INVOICED|OVERDUE → PAID} に遷移したときに
 * shared モジュールのイベントとして発行する。bookingms の {@code CrossBillingPaymentHandler}
 * （{@code @ProcessingGroup("cross-booking-billing")}）が購読し、{@code Cargo} 集約を
 * {@code SETTLED} 状態に遷移させる（US23 受入基準 4）。</p>
 *
 * <p>cross-service の安定契約として shared モジュールに配置し、billingms / bookingms が
 * 同一 FQCN でシリアライズ・デシリアライズできるようにする（IT7 T4.5）。</p>
 *
 * <p>billingms 内部 {@code com.example.billingms.domain.events.PaymentRecordedEvent} とは
 * 同一意味の event だが、cross-service 配信用にこの shared 版を別途発行する設計とすることで、
 * 内部 event の payload 変更（例: 追加のメタデータ）が cross-service 契約に影響しないように保護する。</p>
 *
 * @param invoiceId   Invoice 識別子
 * @param paymentId   入金識別子
 * @param bookingId   予約識別子（bookingms の Cargo 集約の関連付けキー）
 * @param shipperId   荷主識別子
 * @param paidAmount  入金額
 * @param currency    通貨コード（ISO 4217 3 文字）
 * @param paidAt      入金時刻
 * @param recordedAt  記録時刻
 */
public record PaymentRecordedEvent(
        String invoiceId,
        String paymentId,
        String bookingId,
        String shipperId,
        BigDecimal paidAmount,
        String currency,
        LocalDateTime paidAt,
        LocalDateTime recordedAt
) {
}
