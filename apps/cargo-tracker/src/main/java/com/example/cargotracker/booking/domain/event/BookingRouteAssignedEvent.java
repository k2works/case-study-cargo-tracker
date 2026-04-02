package com.example.cargotracker.booking.domain.event;

import com.example.cargotracker.booking.domain.model.aggregates.BookingId;
import com.example.cargotracker.booking.domain.model.valueobjects.AssignedRoute;

public record BookingRouteAssignedEvent(
        BookingId bookingId,
        AssignedRoute assignedRoute
) implements DomainEvent {
}
