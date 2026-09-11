package com.example.cargotracker.billing.domain.model.events;

import java.math.BigDecimal;
import java.time.Instant;
import org.axonframework.eventsourcing.annotation.EventTag;

/**
 * 料金を調整した（billingms の内部イベント / US21 §受入基準 6）。
 *
 * <p><b>合計も載せる。</b> 投影はコマンドも他のイベントも読まずにこの 1 本で
 * 行を更新する。差分だけを載せると、投影が自分で足し算をすることになり、
 * 集約と投影が別々に合計を持つ（食い違う余地を作る）。</p>
 */
public record InvoiceAdjustedEvent(
        @EventTag(key = "invoiceId") String invoiceId,
        BigDecimal amount,
        String reason,
        String basisExceptionId,
        BigDecimal adjustmentTotal,
        BigDecimal taxAmount,
        BigDecimal totalAmount,
        String currency,
        String adjustedBy,
        Instant adjustedAt) {
}
