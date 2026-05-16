package com.example.cargotracker.routingms.domain.model.valueobjects;

import java.time.LocalDateTime;
import java.util.List;

public record TransitEdge(
        String voyageNumber,
        String fromUnLocode,
        String toUnLocode,
        LocalDateTime departureTime,
        LocalDateTime arrivalTime,
        List<String> acceptedCargoTypes
) {}
