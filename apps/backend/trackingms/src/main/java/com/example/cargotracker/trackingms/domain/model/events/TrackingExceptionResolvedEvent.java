package com.example.cargotracker.trackingms.domain.model.events;

import com.example.cargotracker.trackingms.domain.model.valueobjects.TrackingNumber;
import java.time.LocalDateTime;

/**
 * 追跡例外が解決されたドメインイベント（US20）。
 */
public record TrackingExceptionResolvedEvent(
        TrackingNumber trackingNumber,
        String exceptionId,
        String resolution,
        LocalDateTime resolvedAt,
        String operatorId) { }
