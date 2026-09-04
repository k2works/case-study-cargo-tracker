package com.example.cargotracker.booking.domain.model.events;

import org.axonframework.eventsourcing.annotation.EventTag;

/**
 * 経路設計を依頼した（UC04 / US06）。
 *
 * <p>契約イベントではない。経路設計者は routingms ではなく bookingms の予約を見る
 * （{@code routing_read_db} に予約の写しを作らない）ので、他サービスの購読者はいない。</p>
 *
 * <p><b>{@code @EventTag} が要る。</b> 付け忘れると集約は空のまま復元され、
 * 状態遷移の検査が丸ごと素通りする。</p>
 */
public record RoutingRequestedEvent(
        @EventTag(key = "bookingId") String bookingId,
        String requestedBy) {
}
