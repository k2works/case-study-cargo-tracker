package com.example.cargotracker.booking.domain.model.valueobjects;

import com.example.cargotracker.shared.domain.model.Location;

import java.time.LocalDate;
import java.util.Objects;

public final class RouteSpecification {

    private final Location origin;
    private final Location destination;
    private final LocalDate arrivalDeadline;

    public RouteSpecification(Location origin, Location destination, LocalDate arrivalDeadline) {
        this.origin = Objects.requireNonNull(origin, "origin must not be null");
        this.destination = Objects.requireNonNull(destination, "destination must not be null");
        this.arrivalDeadline = Objects.requireNonNull(arrivalDeadline, "arrivalDeadline must not be null");

        if (this.origin.equals(this.destination)) {
            throw new IllegalArgumentException("origin and destination must be different");
        }
        if (this.arrivalDeadline.isBefore(LocalDate.now())) {
            throw new IllegalArgumentException("arrivalDeadline must not be in the past");
        }
    }

    public Location getOrigin() {
        return origin;
    }

    public Location getDestination() {
        return destination;
    }

    public LocalDate getArrivalDeadline() {
        return arrivalDeadline;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof RouteSpecification that)) {
            return false;
        }
        return Objects.equals(origin, that.origin)
                && Objects.equals(destination, that.destination)
                && Objects.equals(arrivalDeadline, that.arrivalDeadline);
    }

    @Override
    public int hashCode() {
        return Objects.hash(origin, destination, arrivalDeadline);
    }
}
