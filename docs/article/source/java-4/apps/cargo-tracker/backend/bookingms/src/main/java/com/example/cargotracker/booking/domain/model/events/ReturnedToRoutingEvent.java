package com.example.cargotracker.booking.domain.model.events;

import java.time.Instant;
import org.axonframework.eventsourcing.annotation.EventTag;

/**
 * 通知した経路を経路設計へ戻した（UC08 / US12）。
 *
 * <p><b>{@code RoutingRequestedEvent} を再利用しない。</b> 投影の結果（作業一覧に
 * 再び出る）は同じでも、「引き渡した」と「通知後に戻した」を履歴で区別できなく
 * なる。経路設計者が「なぜ戻ってきたのか」を読めることが業務の要点である。</p>
 *
 * <p><b>確定済みの旅程は消さない。</b> 再設計で入れ替わるまで残す。</p>
 */
public record ReturnedToRoutingEvent(
        @EventTag(key = "bookingId") String bookingId,
        String reason,
        String returnedBy,
        Instant returnedAt) {
}
