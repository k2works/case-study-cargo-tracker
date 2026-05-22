package com.example.routingms.domain.events;

import com.example.routingms.domain.commands.RegisterVoyageCommand.CarrierMovementData;

import java.time.LocalDateTime;
import java.util.List;

public class VoyageRegisteredEvent {

    private final String voyageNumber;
    private final String carrierCode;
    private final String carrierName;
    private final String shipName;
    private final String originUnlocode;
    private final String destUnlocode;
    private final LocalDateTime departureDate;
    private final LocalDateTime arrivalDate;
    private final List<CarrierMovementData> movements;
    private final List<String> acceptedCargoTypes;

    public VoyageRegisteredEvent(
            String voyageNumber,
            String carrierCode,
            String carrierName,
            String shipName,
            String originUnlocode,
            String destUnlocode,
            LocalDateTime departureDate,
            LocalDateTime arrivalDate,
            List<CarrierMovementData> movements,
            List<String> acceptedCargoTypes) {
        this.voyageNumber = voyageNumber;
        this.carrierCode = carrierCode;
        this.carrierName = carrierName;
        this.shipName = shipName;
        this.originUnlocode = originUnlocode;
        this.destUnlocode = destUnlocode;
        this.departureDate = departureDate;
        this.arrivalDate = arrivalDate;
        this.movements = movements;
        this.acceptedCargoTypes = acceptedCargoTypes;
    }

    public String getVoyageNumber() { return voyageNumber; }
    public String getCarrierCode() { return carrierCode; }
    public String getCarrierName() { return carrierName; }
    public String getShipName() { return shipName; }
    public String getOriginUnlocode() { return originUnlocode; }
    public String getDestUnlocode() { return destUnlocode; }
    public LocalDateTime getDepartureDate() { return departureDate; }
    public LocalDateTime getArrivalDate() { return arrivalDate; }
    public List<CarrierMovementData> getMovements() { return movements; }
    public List<String> getAcceptedCargoTypes() { return acceptedCargoTypes; }
}
