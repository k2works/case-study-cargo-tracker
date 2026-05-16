package com.example.cargotracker.routingms.interfaces.rest.dto;

import java.time.LocalDate;

public record SelectRouteRequest(
        String origin,
        String destination,
        LocalDate arrivalDeadline,
        String cargoType,
        int selectedIndex
) {}
