package com.example.cargotracker.bookingms.domain.model.valueobjects;

import java.time.LocalDate;
import java.util.Objects;

/**
 * 輸送経路の仕様（出発地・目的地・到着期限）。
 */
public record RouteSpecification(Location origin, Location destination, LocalDate arrivalDeadline) {

    public RouteSpecification {
        Objects.requireNonNull(origin, "origin");
        Objects.requireNonNull(destination, "destination");
        Objects.requireNonNull(arrivalDeadline, "arrivalDeadline");
        if (origin.unLocode().equals(destination.unLocode())) {
            throw new IllegalArgumentException("origin と destination は同一にできません: " + origin.unLocode().value());
        }
    }
}
