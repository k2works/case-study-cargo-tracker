package com.example.routingms.domain.events;

import com.example.routingms.domain.commands.RegisterVoyageCommand.CarrierMovementData;

import java.time.LocalDateTime;
import java.util.List;

@SuppressWarnings("java:S107") // Axon Event は全航海属性を必要とするため許容
public record VoyageRegisteredEvent(
        String voyageNumber,
        String carrierCode,
        String carrierName,
        String shipName,
        String originUnlocode,
        String destUnlocode,
        LocalDateTime departureDate,
        LocalDateTime arrivalDate,
        List<CarrierMovementData> movements,
        List<String> acceptedCargoTypes
) {}
