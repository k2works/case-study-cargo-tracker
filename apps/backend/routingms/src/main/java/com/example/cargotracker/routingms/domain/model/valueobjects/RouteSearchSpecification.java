package com.example.cargotracker.routingms.domain.model.valueobjects;

import java.time.LocalDate;

public record RouteSearchSpecification(
        String origin,
        String destination,
        LocalDate arrivalDeadline,
        CargoType cargoType
) {}
