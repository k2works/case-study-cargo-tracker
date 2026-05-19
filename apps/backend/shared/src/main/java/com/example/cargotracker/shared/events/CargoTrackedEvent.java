package com.example.cargotracker.shared.events;

import org.axonframework.eventsourcing.annotation.EventTag;

/**
 * 追跡番号発行完了イベント（shared kernel）。
 *
 * <p>bookingms から発行され、trackingms の CargoEventAclHandler が購読して
 * InitializeTrackingCommand を内部発行する（ADR-0012・ADR-0014）。</p>
 */
public record CargoTrackedEvent(@EventTag String bookingId, String trackingNumber) {
}
