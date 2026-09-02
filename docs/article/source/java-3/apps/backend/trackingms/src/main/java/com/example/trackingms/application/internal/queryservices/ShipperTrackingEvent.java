package com.example.trackingms.application.internal.queryservices;

import com.example.trackingms.domain.model.valueobjects.TrackingEvent;

/** 荷主向け追跡詳細の経過 1 件。 */
public record ShipperTrackingEvent(String occurredAt, String status, String statusLabel,
        String locationName) {

    static ShipperTrackingEvent from(TrackingEvent event, java.time.ZoneId zone) {
        return new ShipperTrackingEvent(
                java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
                        .format(event.occurredAt().atZone(zone)),
                event.trackingStatus().name(), event.trackingStatus().label(),
                event.location().name());
    }
}
