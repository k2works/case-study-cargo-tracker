package com.example.cargotracker.booking.domain.model.events;

import java.time.Instant;
import org.axonframework.eventsourcing.annotation.EventTag;

/**
 * キャンセル申請が承認された（UC22 / US30 §受入基準 5・6）。
 *
 * <p><b>判断そのものを残す。</b> 予約がキャンセルになったことは
 * {@code CargoCancelledEvent} が伝えるが、<b>誰が・いつ・どこで降ろすと決めたか</b>は
 * 申請の履歴（S22）が読む——記録と読み口は対で出す。</p>
 */
public record CancellationApprovedEvent(
        @EventTag(key = "bookingId") String bookingId,
        String requestId,
        String dischargeUnLocode,
        String reason,
        String approvedBy,
        Instant approvedAt) {
}
