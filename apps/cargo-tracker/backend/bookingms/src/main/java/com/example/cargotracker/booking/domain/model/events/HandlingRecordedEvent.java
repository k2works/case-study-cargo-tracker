package com.example.cargotracker.booking.domain.model.events;

import java.time.Instant;
import org.axonframework.eventsourcing.annotation.EventTag;

/**
 * 荷役が記録されたことを予約に写した（US15 / 不変条件 12）。
 *
 * <p><b>bookingms の内部イベント。</b> 荷役そのものの真実は handlingms にあり、
 * ここに残すのは「予約から見た最後の荷役」である。</p>
 *
 * <p><b>{@code @EventTag} が要る。</b> 付け忘れると集約は空のまま復元され、
 * 「最初の受領で輸送中にする」判断が毎回やり直される。</p>
 */
public record HandlingRecordedEvent(
        @EventTag(key = "bookingId") String bookingId,
        String activityId,
        String handlingType,
        String unLocode,
        Instant completedAt,
        Instant recordedAt) {
}
