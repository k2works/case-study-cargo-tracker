package com.example.trackingms.application.internal.queryservices;

/** bookingms から受け取る、荷主境界の判定に必要な最小 Snapshot。 */
public record ShipperCargoSnapshot(String bookingId, String trackingNumber, Long shipperId) {
}
