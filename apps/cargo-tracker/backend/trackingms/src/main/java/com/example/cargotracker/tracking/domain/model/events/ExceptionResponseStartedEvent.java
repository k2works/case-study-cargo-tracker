package com.example.cargotracker.tracking.domain.model.events;

import java.time.Instant;
import org.axonframework.eventsourcing.annotation.EventTag;

/** 例外への対応が始まった（UC16 / US19 §受入基準 4）。 */
public record ExceptionResponseStartedEvent(
        @EventTag(key = "trackingNumber") String trackingNumber,
        String exceptionId,
        String newEstimatedArrival,
        String plan,
        String respondedBy,
        Instant startedAt) {
}
