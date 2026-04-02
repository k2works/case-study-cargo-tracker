package com.example.cargotracker.booking.domain.event;

import com.example.cargotracker.booking.domain.model.aggregates.BookingId;

public record BookingConfirmedEvent(BookingId bookingId) implements DomainEvent {
}
