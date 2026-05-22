package com.example.routingms.interfaces.rest.dto;

import java.time.LocalDateTime;
import java.util.List;

public class RegisterVoyageRequest {

    private String voyageNumber;
    private String carrierCode;
    private String carrierName;
    private String shipName;
    private String originUnlocode;
    private String destUnlocode;
    private LocalDateTime departureDate;
    private LocalDateTime arrivalDate;
    private List<CarrierMovementRequest> movements;
    private List<String> acceptedCargoTypes;

    public RegisterVoyageRequest() {}

    public String getVoyageNumber() { return voyageNumber; }
    public void setVoyageNumber(String voyageNumber) { this.voyageNumber = voyageNumber; }

    public String getCarrierCode() { return carrierCode; }
    public void setCarrierCode(String carrierCode) { this.carrierCode = carrierCode; }

    public String getCarrierName() { return carrierName; }
    public void setCarrierName(String carrierName) { this.carrierName = carrierName; }

    public String getShipName() { return shipName; }
    public void setShipName(String shipName) { this.shipName = shipName; }

    public String getOriginUnlocode() { return originUnlocode; }
    public void setOriginUnlocode(String originUnlocode) { this.originUnlocode = originUnlocode; }

    public String getDestUnlocode() { return destUnlocode; }
    public void setDestUnlocode(String destUnlocode) { this.destUnlocode = destUnlocode; }

    public LocalDateTime getDepartureDate() { return departureDate; }
    public void setDepartureDate(LocalDateTime departureDate) { this.departureDate = departureDate; }

    public LocalDateTime getArrivalDate() { return arrivalDate; }
    public void setArrivalDate(LocalDateTime arrivalDate) { this.arrivalDate = arrivalDate; }

    public List<CarrierMovementRequest> getMovements() { return movements; }
    public void setMovements(List<CarrierMovementRequest> movements) { this.movements = movements; }

    public List<String> getAcceptedCargoTypes() { return acceptedCargoTypes; }
    public void setAcceptedCargoTypes(List<String> acceptedCargoTypes) { this.acceptedCargoTypes = acceptedCargoTypes; }

    public static class CarrierMovementRequest {
        private String departureUnlocode;
        private String arrivalUnlocode;
        private LocalDateTime departureTime;
        private LocalDateTime arrivalTime;

        public CarrierMovementRequest() {}

        public String getDepartureUnlocode() { return departureUnlocode; }
        public void setDepartureUnlocode(String departureUnlocode) { this.departureUnlocode = departureUnlocode; }

        public String getArrivalUnlocode() { return arrivalUnlocode; }
        public void setArrivalUnlocode(String arrivalUnlocode) { this.arrivalUnlocode = arrivalUnlocode; }

        public LocalDateTime getDepartureTime() { return departureTime; }
        public void setDepartureTime(LocalDateTime departureTime) { this.departureTime = departureTime; }

        public LocalDateTime getArrivalTime() { return arrivalTime; }
        public void setArrivalTime(LocalDateTime arrivalTime) { this.arrivalTime = arrivalTime; }
    }
}
