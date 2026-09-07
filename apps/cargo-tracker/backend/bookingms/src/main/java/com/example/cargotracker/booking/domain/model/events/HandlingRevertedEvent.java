package com.example.cargotracker.booking.domain.model.events;

import java.time.Instant;
import org.axonframework.eventsourcing.annotation.EventTag;

/**
 * 取り消された荷役の分を予約から戻した（不変条件 13）。
 *
 * <p><b>誤配が解けたかどうかも載せる。</b> 購読側（投影）はコマンドを読まず、
 * 直前の状態も引き直さない。イベントは購読側の投影が作れる分を運ぶ。</p>
 */
public record HandlingRevertedEvent(
        @EventTag(key = "bookingId") String bookingId,
        String activityId,
        boolean misrouteCleared,
        Instant revertedAt) {
}
