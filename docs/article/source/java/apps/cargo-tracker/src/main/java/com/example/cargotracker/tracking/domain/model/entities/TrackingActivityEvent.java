package com.example.cargotracker.tracking.domain.model.entities;

import com.example.cargotracker.tracking.domain.model.valueobjects.TrackingEventType;

import java.time.LocalDateTime;

public class TrackingActivityEvent {

    private final TrackingEventType eventType;
    private final String locationUnlocode;
    private final LocalDateTime completionTime;
    private final String voyageNumber;

    public TrackingActivityEvent(
            TrackingEventType eventType,
            String locationUnlocode,
            LocalDateTime completionTime,
            String voyageNumber) {
        if (eventType == null) throw new IllegalArgumentException("eventType must not be null");
        if (locationUnlocode == null || locationUnlocode.isBlank())
            throw new IllegalArgumentException("locationUnlocode must not be blank");
        if (completionTime == null) throw new IllegalArgumentException("completionTime must not be null");
        this.eventType = eventType;
        this.locationUnlocode = locationUnlocode;
        this.completionTime = completionTime;
        this.voyageNumber = voyageNumber;
    }

    public TrackingEventType getEventType() {
        return eventType;
    }

    public String getLocationUnlocode() {
        return locationUnlocode;
    }

    public LocalDateTime getCompletionTime() {
        return completionTime;
    }

    public String getVoyageNumber() {
        return voyageNumber;
    }
}
