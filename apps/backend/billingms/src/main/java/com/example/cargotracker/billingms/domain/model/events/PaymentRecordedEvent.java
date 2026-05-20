package com.example.cargotracker.billingms.domain.model.events;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import org.axonframework.eventsourcing.annotation.EventTag;

/**
 * 入金確認・精算完了イベント（US23）。
 *
 * @param invoiceId     請求 ID
 * @param paidAmount    入金額
 * @param paymentMethod 決済方法
 * @param paidAt        入金日時
 * @param operatorId    操作者 ID
 */
public record PaymentRecordedEvent(
        @EventTag String invoiceId,
        BigDecimal paidAmount,
        String paymentMethod,
        LocalDateTime paidAt,
        String operatorId) {
}
