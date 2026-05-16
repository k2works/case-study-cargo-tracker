package com.example.cargotracker.routingms.domain.model.valueobjects;

import java.time.LocalDateTime;
import java.util.Set;

public record TransitEdge(
        String voyageNumber,
        UnLocode fromUnLocode,
        UnLocode toUnLocode,
        LocalDateTime departureTime,
        LocalDateTime arrivalTime,
        Set<CargoType> acceptedCargoTypes
) {

    public static TransitEdge of(
            String voyageNumber,
            String fromUnLocode,
            String toUnLocode,
            LocalDateTime departureTime,
            LocalDateTime arrivalTime,
            Set<CargoType> acceptedCargoTypes) {
        return new TransitEdge(
                voyageNumber,
                new UnLocode(fromUnLocode),
                new UnLocode(toUnLocode),
                departureTime,
                arrivalTime,
                acceptedCargoTypes);
    }
}
