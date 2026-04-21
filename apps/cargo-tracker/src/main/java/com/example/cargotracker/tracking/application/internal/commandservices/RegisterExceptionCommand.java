package com.example.cargotracker.tracking.application.internal.commandservices;

import com.example.cargotracker.tracking.domain.model.valueobjects.ExceptionType;

import java.time.LocalDateTime;

public record RegisterExceptionCommand(
        String trackingNumber,
        ExceptionType exceptionType,
        String locationUnlocode,
        LocalDateTime occurrenceTime,
        String reason,
        String responseNote
) {}
