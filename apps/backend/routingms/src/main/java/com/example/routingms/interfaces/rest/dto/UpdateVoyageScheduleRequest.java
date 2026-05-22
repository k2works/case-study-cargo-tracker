package com.example.routingms.interfaces.rest.dto;

import java.time.LocalDateTime;
import java.util.List;

public class UpdateVoyageScheduleRequest {

    private LocalDateTime departureDate;
    private LocalDateTime arrivalDate;
    private List<RegisterVoyageRequest.CarrierMovementRequest> movements;
    private List<String> acceptedCargoTypes;

    public UpdateVoyageScheduleRequest() {}

    public LocalDateTime getDepartureDate() { return departureDate; }
    public void setDepartureDate(LocalDateTime departureDate) { this.departureDate = departureDate; }

    public LocalDateTime getArrivalDate() { return arrivalDate; }
    public void setArrivalDate(LocalDateTime arrivalDate) { this.arrivalDate = arrivalDate; }

    public List<RegisterVoyageRequest.CarrierMovementRequest> getMovements() { return movements; }
    public void setMovements(List<RegisterVoyageRequest.CarrierMovementRequest> movements) { this.movements = movements; }

    public List<String> getAcceptedCargoTypes() { return acceptedCargoTypes; }
    public void setAcceptedCargoTypes(List<String> acceptedCargoTypes) { this.acceptedCargoTypes = acceptedCargoTypes; }
}
