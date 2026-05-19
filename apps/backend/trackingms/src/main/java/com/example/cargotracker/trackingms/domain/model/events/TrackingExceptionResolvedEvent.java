package com.example.cargotracker.trackingms.domain.model.events;

import com.example.cargotracker.trackingms.domain.model.valueobjects.TrackingNumber;
import java.time.LocalDateTime;
import org.axonframework.eventsourcing.annotation.EventTag;

/**
 * 追跡例外が解決されたドメインイベント（US20）。
 */
public record TrackingExceptionResolvedEvent(
        @EventTag TrackingNumber trackingNumber,
        String exceptionId,
        String resolution,
        LocalDateTime resolvedAt,
        String operatorId) { }
