package com.example.cargotracker.tracking.application.internal.outboundservices;

import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

/**
 * 予約情報照会ポート — tracking BC が booking BC の予約情報を取得するための ACL ポート。
 */
public interface BookingInfoQueryPort {

    Optional<BookingSummary> findById(UUID bookingId);

    record BookingSummary(
            String originLocation,
            String destinationLocation,
            LocalDate requestedDeliveryDate
    ) {}
}
