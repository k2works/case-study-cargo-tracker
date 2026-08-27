package com.example.trackingms.application.internal;

import java.util.List;

/** 荷主向け追跡詳細。自社貨物であることを確認したあとにだけ作る。 */
public record ShipperTrackingDetail(String trackingNumber, String status, String statusLabel,
        String locationName, java.time.LocalDate estimatedArrival, boolean hasException,
        boolean urgent, List<ShipperTrackingEvent> events) {

    static ShipperTrackingDetail from(ShipperTrackingSummary summary,
            List<ShipperTrackingEvent> events) {
        return new ShipperTrackingDetail(summary.trackingNumber(), summary.status(),
                summary.statusLabel(), summary.locationName(), summary.estimatedArrival(),
                summary.hasException(), summary.urgent(), events);
    }
}
