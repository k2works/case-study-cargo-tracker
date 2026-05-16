package com.example.cargotracker.routingms.domain.model.valueobjects;

import java.time.Duration;
import java.time.LocalDate;

public record RouteSearchSpecification(
        UnLocode origin,
        UnLocode destination,
        LocalDate arrivalDeadline,
        CargoType cargoType,
        Duration minimumTransferTime,
        int maxLegs
) {

    public static final Duration DEFAULT_MINIMUM_TRANSFER_TIME = Duration.ofHours(24);
    public static final int DEFAULT_MAX_LEGS = 5;

    public static RouteSearchSpecification of(
            String origin,
            String destination,
            LocalDate arrivalDeadline,
            CargoType cargoType) {
        return new RouteSearchSpecification(
                new UnLocode(origin),
                new UnLocode(destination),
                arrivalDeadline,
                cargoType,
                DEFAULT_MINIMUM_TRANSFER_TIME,
                DEFAULT_MAX_LEGS);
    }
}
