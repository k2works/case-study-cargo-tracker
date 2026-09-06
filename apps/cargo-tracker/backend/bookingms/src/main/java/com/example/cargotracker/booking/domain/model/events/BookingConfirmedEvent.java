package com.example.cargotracker.booking.domain.model.events;

import java.time.Instant;
import org.axonframework.eventsourcing.annotation.EventTag;

/**
 * 予約が確定した（UC11 / US13）。
 *
 * <p>ここから先は追跡番号の発行（US14）に進む。<b>経路設計へは戻せない</b>
 * （遷移表に {@code CONFIRMED → ROUTE_PROPOSED} は無い）。荷主が変更を求めたら
 * キャンセル（US30）を通す。</p>
 *
 * <p><b>{@code @EventTag} が要る。</b> 付け忘れると集約は空のまま復元され、
 * 「二重に確定しない」「通知していない予約は確定できない」守りが素通りする。</p>
 */
public record BookingConfirmedEvent(
        @EventTag(key = "bookingId") String bookingId,
        String confirmedBy,
        Instant confirmedAt) {
}
