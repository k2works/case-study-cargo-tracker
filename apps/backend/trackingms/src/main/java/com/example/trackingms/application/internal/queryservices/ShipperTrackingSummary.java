package com.example.trackingms.application.internal.queryservices;

import com.example.trackingms.domain.model.TrackingActivity;
import java.time.LocalDate;

/** 荷主向け追跡一覧の 1 件。 */
public record ShipperTrackingSummary(String trackingNumber, String status, String statusLabel,
        String locationName, LocalDate estimatedArrival, boolean hasException, boolean urgent) {

    static ShipperTrackingSummary from(TrackingActivity activity) {
        return new ShipperTrackingSummary(activity.trackingNumber().value(),
                activity.trackingStatus().name(), activity.trackingStatus().label(),
                activity.currentLocation().name(), activity.estimatedArrival().orElse(null),
                activity.activeException().isPresent(), activity.hasUrgentException());
    }
}
