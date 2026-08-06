package com.example.cargotracker.booking.domain.model.valueobjects;

import com.example.cargotracker.shared.domain.model.Location;

import java.time.LocalDateTime;
import java.util.Objects;

public record Leg(
        String voyageNumber,
        Location loadLocation,
        Location unloadLocation,
        LocalDateTime loadTime,
        LocalDateTime unloadTime
) {
    public Leg {
        Objects.requireNonNull(voyageNumber, "voyageNumber must not be null");
        Objects.requireNonNull(loadLocation, "loadLocation must not be null");
        Objects.requireNonNull(unloadLocation, "unloadLocation must not be null");
        Objects.requireNonNull(loadTime, "loadTime must not be null");
        Objects.requireNonNull(unloadTime, "unloadTime must not be null");

        if (voyageNumber.isBlank()) {
            throw new IllegalArgumentException("voyageNumber must not be blank");
        }
        if (!loadTime.isBefore(unloadTime)) {
            throw new IllegalArgumentException("loadTime must be before unloadTime");
        }
    }
}
