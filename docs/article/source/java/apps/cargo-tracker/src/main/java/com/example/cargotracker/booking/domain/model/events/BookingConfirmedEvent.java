package com.example.cargotracker.booking.domain.model.events;

import com.example.cargotracker.booking.domain.model.valueobjects.BookingId;

public record BookingConfirmedEvent(BookingId bookingId) {
}
