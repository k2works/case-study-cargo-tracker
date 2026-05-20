package com.example.cargotracker.billingms.domain.model.events;

import java.time.LocalDate;
import org.axonframework.eventsourcing.annotation.EventTag;

/**
 * 精算書発行イベント（US23）。
 *
 * @param invoiceId    請求 ID
 * @param invoiceNumber 発行された請求番号
 * @param paymentDue   支払期限
 * @param operatorId   操作者 ID
 */
public record InvoiceIssuedEvent(
        @EventTag String invoiceId,
        String invoiceNumber,
        LocalDate paymentDue,
        String operatorId) {
}
