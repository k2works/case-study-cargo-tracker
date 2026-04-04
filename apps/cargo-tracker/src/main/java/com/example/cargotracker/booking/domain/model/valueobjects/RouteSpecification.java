package com.example.cargotracker.booking.domain.model.valueobjects;

import com.example.cargotracker.shared.domain.model.Location;

import java.time.LocalDate;
import java.util.Objects;

public record RouteSpecification(Location origin, Location destination, LocalDate arrivalDeadline) {

    public RouteSpecification {
        Objects.requireNonNull(origin, "origin must not be null");
        Objects.requireNonNull(destination, "destination must not be null");
        Objects.requireNonNull(arrivalDeadline, "arrivalDeadline must not be null");

        if (origin.equals(destination)) {
            throw new IllegalArgumentException("origin and destination must be different");
        }
        if (arrivalDeadline.isBefore(LocalDate.now())) {
            throw new IllegalArgumentException("arrivalDeadline must not be in the past");
        }
    }
}
