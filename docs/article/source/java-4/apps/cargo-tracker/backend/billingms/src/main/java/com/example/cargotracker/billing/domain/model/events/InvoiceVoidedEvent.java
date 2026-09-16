package com.example.cargotracker.billing.domain.model.events;

import java.time.Instant;
import org.axonframework.eventsourcing.annotation.EventTag;

/**
 * 請求書を取り消した（billingms の内部イベント / UC18）。
 *
 * <p><b>理由を載せる。</b> 取り消した請求書は荷主にも見えなくなるので、
 * 何が起きたかを追えなければ、あとから誰も確かめられない。</p>
 *
 * <p><b>投影は行を消さない。</b> 状態を {@code VOID} にして既定の一覧から外す
 * だけである。消すと、取り消した事実そのものが残らない。</p>
 */
public record InvoiceVoidedEvent(
        @EventTag(key = "invoiceId") String invoiceId,
        String bookingId,
        String reason,
        String voidedBy,
        Instant voidedAt) {
}
