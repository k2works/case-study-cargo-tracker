package com.example.cargotracker.tracking.application.internal.commandservices;

import com.example.cargotracker.tracking.domain.model.valueobjects.CargoTrackingStatus;

import java.time.LocalDateTime;

public record ManualStatusUpdateCommand(
        String trackingNumber,
        CargoTrackingStatus newStatus,
        String locationUnlocode,
        LocalDateTime updateTime
) {}
