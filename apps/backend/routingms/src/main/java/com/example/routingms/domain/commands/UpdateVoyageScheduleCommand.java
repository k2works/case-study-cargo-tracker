package com.example.routingms.domain.commands;

import com.example.routingms.domain.commands.RegisterVoyageCommand.CarrierMovementData;
import org.axonframework.modelling.command.TargetAggregateIdentifier;

import java.time.LocalDateTime;
import java.util.List;

public record UpdateVoyageScheduleCommand(
        @TargetAggregateIdentifier String voyageNumber,
        LocalDateTime departureDate,
        LocalDateTime arrivalDate,
        List<CarrierMovementData> movements,
        List<String> acceptedCargoTypes
) {}
