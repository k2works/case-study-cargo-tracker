package com.example.cargotracker.booking.domain.model.events;

import java.time.Instant;
import org.axonframework.eventsourcing.annotation.EventTag;

/**
 * 追跡番号の発行を取り消した（US14 の補償 / ADR-0010 決定 4）。
 *
 * <p>予約は {@code CONFIRMED} に戻り、経路設計者がもう一度発行できる。
 * <b>キャンセルではない</b>——荷物も予約も生きている。</p>
 *
 * <p><b>{@code @EventTag} が要る。</b> 付け忘れると集約は空のまま復元され、
 * 「発行済みのものだけ取り消せる」守りが素通りする。</p>
 */
public record TrackingNumberRevertedEvent(
        @EventTag(key = "bookingId") String bookingId,
        String trackingNumber,
        String reason,
        Instant revertedAt) {
}
