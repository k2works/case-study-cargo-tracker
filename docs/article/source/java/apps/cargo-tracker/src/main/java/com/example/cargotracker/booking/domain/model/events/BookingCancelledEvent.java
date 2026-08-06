package com.example.cargotracker.booking.domain.model.events;

import com.example.cargotracker.booking.domain.model.valueobjects.BookingId;

public record BookingCancelledEvent(BookingId bookingId) {
}
