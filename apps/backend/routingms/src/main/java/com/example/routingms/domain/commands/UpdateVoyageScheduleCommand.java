package com.example.routingms.domain.commands;

import com.example.routingms.domain.commands.RegisterVoyageCommand.CarrierMovementData;
import org.axonframework.modelling.command.TargetAggregateIdentifier;

import java.time.LocalDateTime;
import java.util.List;

public class UpdateVoyageScheduleCommand {

    @TargetAggregateIdentifier
    private final String voyageNumber;
    private final LocalDateTime departureDate;
    private final LocalDateTime arrivalDate;
    private final List<CarrierMovementData> movements;
    private final List<String> acceptedCargoTypes;

    public UpdateVoyageScheduleCommand(
            String voyageNumber,
            LocalDateTime departureDate,
            LocalDateTime arrivalDate,
            List<CarrierMovementData> movements,
            List<String> acceptedCargoTypes) {
        this.voyageNumber = voyageNumber;
        this.departureDate = departureDate;
        this.arrivalDate = arrivalDate;
        this.movements = movements;
        this.acceptedCargoTypes = acceptedCargoTypes;
    }

    public String getVoyageNumber() { return voyageNumber; }
    public LocalDateTime getDepartureDate() { return departureDate; }
    public LocalDateTime getArrivalDate() { return arrivalDate; }
    public List<CarrierMovementData> getMovements() { return movements; }
    public List<String> getAcceptedCargoTypes() { return acceptedCargoTypes; }
}
