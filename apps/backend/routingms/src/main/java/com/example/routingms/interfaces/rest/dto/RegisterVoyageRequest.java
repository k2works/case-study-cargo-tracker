package com.example.routingms.interfaces.rest.dto;

import java.time.LocalDateTime;
import java.util.List;

public record RegisterVoyageRequest(
        String voyageNumber,
        String carrierCode,
        String carrierName,
        String shipName,
        String originUnlocode,
        String destUnlocode,
        LocalDateTime departureDate,
        LocalDateTime arrivalDate,
        List<CarrierMovementRequest> movements,
        List<String> acceptedCargoTypes
) {
    public record CarrierMovementRequest(
            String departureUnlocode,
            String arrivalUnlocode,
            LocalDateTime departureTime,
            LocalDateTime arrivalTime
    ) {}
}
