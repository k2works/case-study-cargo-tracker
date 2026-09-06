package com.example.cargotracker.booking.domain.model.events;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import org.axonframework.eventsourcing.annotation.EventTag;

/**
 * 経路の条件を調整した（UC08 / US10）。
 *
 * <p>誰がいつ何に変えたかを残す。<b>{@code @EventTag} が要る。</b> 付け忘れると
 * 集約は空のまま復元され、状態を見る守りが丸ごと素通りする。</p>
 *
 * <p>契約イベントではない。bookingms 内で完結する。</p>
 */
public record RouteSpecificationAdjustedEvent(
        @EventTag(key = "bookingId") String bookingId,
        LocalDate arrivalDeadline,
        List<String> excludeUnLocodes,
        String departFromUnLocode,
        String adjustedBy,
        Instant adjustedAt) {
}
