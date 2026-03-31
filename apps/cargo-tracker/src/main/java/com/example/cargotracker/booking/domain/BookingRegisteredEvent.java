package com.example.cargotracker.booking.domain;

import com.example.cargotracker.shipper.domain.ShipperId;

public record BookingRegisteredEvent(BookingId bookingId, ShipperId shipperId) {
}
