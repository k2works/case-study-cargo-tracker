package com.example.routingms.domain.events;

import com.example.routingms.domain.commands.RegisterVoyageCommand.CarrierMovementData;

import java.time.LocalDateTime;
import java.util.List;

public record VoyageScheduleUpdatedEvent(
        String voyageNumber,
        LocalDateTime departureDate,
        LocalDateTime arrivalDate,
        List<CarrierMovementData> movements,
        List<String> acceptedCargoTypes
) {}
