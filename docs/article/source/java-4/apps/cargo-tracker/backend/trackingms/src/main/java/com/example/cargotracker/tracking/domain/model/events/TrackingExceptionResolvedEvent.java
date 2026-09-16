package com.example.cargotracker.tracking.domain.model.events;

import java.time.Instant;
import org.axonframework.eventsourcing.annotation.EventTag;

/**
 * 例外が解決した（UC16 / US19 §受入基準 4）。
 *
 * <p><b>例外そのものは消えない</b>（不変条件 6）。消えるのは「対応が要る」という
 * 状態だけで、起きた事実は料金調整の根拠として残る。</p>
 */
public record TrackingExceptionResolvedEvent(
        @EventTag(key = "trackingNumber") String trackingNumber,
        String exceptionId,
        String resolution,
        String resolvedBy,
        Instant resolvedAt) {
}
