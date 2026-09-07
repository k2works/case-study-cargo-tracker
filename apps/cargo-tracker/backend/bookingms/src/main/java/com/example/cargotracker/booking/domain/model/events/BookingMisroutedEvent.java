package com.example.cargotracker.booking.domain.model.events;

import java.time.Instant;
import org.axonframework.eventsourcing.annotation.EventTag;

/**
 * 予定ルート外の荷役を受けた（US28 / 不変条件 12）。
 *
 * <p><b>trackingms の `CargoMisroutedEvent` とは別の名前にする</b>
 * （domain-model.md）。同名クラスが契約に昇格した瞬間に衝突する。</p>
 *
 * <p>経路設計の状態が誤配になり、現在地起点で組み直すまで作業一覧に残る。</p>
 */
public record BookingMisroutedEvent(
        @EventTag(key = "bookingId") String bookingId,
        String activityId,
        String unLocode,
        Instant detectedAt) {
}
