package com.example.cargotracker.booking.domain.model.events;

import java.time.Instant;
import org.axonframework.eventsourcing.annotation.EventTag;

/**
 * 条件の見直しを営業へ依頼した（UC08 / US10 §受入基準 4）。
 *
 * <p>状態は動かさない（ADR-0009）。投影が
 * {@code condition_review_requested_at} / {@code condition_review_reason} に写し、
 * 営業のダッシュボード（S02）に出す。</p>
 *
 * <p><b>{@code @EventTag} が要る。</b> 付け忘れると集約は空のまま復元される。</p>
 */
public record ConditionReviewRequestedEvent(
        @EventTag(key = "bookingId") String bookingId,
        String reason,
        String requestedBy,
        Instant requestedAt) {
}
