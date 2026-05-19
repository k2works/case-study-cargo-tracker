package com.example.cargotracker.shared.events;

import org.axonframework.eventsourcing.annotation.EventTag;

/**
 * 経路確定イベント（shared kernel）。
 *
 * <p>bookingms から発行され、handlingms の BookingEventAclHandler が購読して
 * cargo_snapshot の booking_status を ROUTE_PROPOSED に更新する。</p>
 */
public record CargoRoutedEvent(@EventTag String bookingId) {
}
