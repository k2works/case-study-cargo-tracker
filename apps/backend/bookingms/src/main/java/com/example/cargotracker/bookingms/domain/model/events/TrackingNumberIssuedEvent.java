package com.example.cargotracker.bookingms.domain.model.events;

import org.axonframework.eventsourcing.annotation.EventTag;

/** 追跡番号発行イベント（US14 / UC12）。 */
public record TrackingNumberIssuedEvent(@EventTag String bookingId, String trackingNumber) {}
