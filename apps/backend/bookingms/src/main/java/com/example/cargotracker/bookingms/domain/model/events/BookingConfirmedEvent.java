package com.example.cargotracker.bookingms.domain.model.events;

import org.axonframework.eventsourcing.annotation.EventTag;

/** 予約確定イベント（US13 / UC11）。 */
public record BookingConfirmedEvent(@EventTag String bookingId) {}
