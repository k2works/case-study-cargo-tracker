package com.example.routingms.domain.commands;

import org.axonframework.modelling.command.TargetAggregateIdentifier;

import java.time.LocalDateTime;
import java.util.List;

@SuppressWarnings("java:S107") // Axon Command は全航海属性を必要とするため許容
public record RegisterVoyageCommand(
        @TargetAggregateIdentifier String voyageNumber,
        String carrierCode,
        String carrierName,
        String shipName,
        String originUnlocode,
        String destUnlocode,
        LocalDateTime departureDate,
        LocalDateTime arrivalDate,
        List<CarrierMovementData> movements,
        List<String> acceptedCargoTypes
) {
    public record CarrierMovementData(
            String departureUnlocode,
            String arrivalUnlocode,
            LocalDateTime departureTime,
            LocalDateTime arrivalTime
    ) {}
}
