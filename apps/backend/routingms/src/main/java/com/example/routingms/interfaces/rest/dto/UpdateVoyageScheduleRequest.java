package com.example.routingms.interfaces.rest.dto;

import java.time.LocalDateTime;
import java.util.List;

public record UpdateVoyageScheduleRequest(
        LocalDateTime departureDate,
        LocalDateTime arrivalDate,
        List<RegisterVoyageRequest.CarrierMovementRequest> movements,
        List<String> acceptedCargoTypes
) {}
