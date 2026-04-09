package com.example.cargotracker.booking.domain.model.valueobjects;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

public record CargoItinerary(List<Leg> legs) {

    public CargoItinerary {
        Objects.requireNonNull(legs, "legs must not be null");
        if (legs.isEmpty()) {
            throw new IllegalArgumentException("legs must not be empty");
        }
        legs = Collections.unmodifiableList(legs);
    }

    public LocalDateTime expectedArrivalTime() {
        return legs.get(legs.size() - 1).unloadTime();
    }
}
