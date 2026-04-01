package com.example.cargotracker.booking.domain.event;

import com.example.cargotracker.booking.domain.model.BookingId;
import com.example.cargotracker.shared.domain.model.ShipperId;

public record BookingRegisteredEvent(BookingId bookingId, ShipperId shipperId) implements DomainEvent {
}
