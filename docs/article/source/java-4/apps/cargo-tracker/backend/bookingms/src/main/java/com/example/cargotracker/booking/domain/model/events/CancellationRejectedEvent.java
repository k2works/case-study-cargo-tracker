package com.example.cargotracker.booking.domain.model.events;

import java.time.Instant;
import org.axonframework.eventsourcing.annotation.EventTag;

/**
 * キャンセル申請が却下された（UC22 / US30 §受入基準 7）。
 *
 * <p><b>予約の状態は動かない。</b> 却下は「このまま運ぶ」という判断である。
 * 申請は決着するので、承認待ちの一覧からは消える——残すと、追跡管理者が毎朝
 * 同じ申請を読み直すことになる。</p>
 */
public record CancellationRejectedEvent(
        @EventTag(key = "bookingId") String bookingId,
        String requestId,
        String reason,
        String rejectedBy,
        Instant rejectedAt) {
}
