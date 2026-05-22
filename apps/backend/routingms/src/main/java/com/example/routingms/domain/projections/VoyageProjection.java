package com.example.routingms.domain.projections;

import java.time.LocalDateTime;
import java.util.List;

public class VoyageProjection {

    private String voyageNumber;
    private String carrierCode;
    private String carrierName;
    private String shipName;
    private LocalDateTime departureDate;
    private LocalDateTime arrivalDate;
    private String originUnlocode;
    private String destUnlocode;
    private String status;
    private List<CarrierMovementProjection> movements;
    private List<String> acceptedCargoTypes;

    public VoyageProjection() { /* MyBatis result mapping */ }

    public String getVoyageNumber() { return voyageNumber; }
    public void setVoyageNumber(String voyageNumber) { this.voyageNumber = voyageNumber; }

    public String getCarrierCode() { return carrierCode; }
    public void setCarrierCode(String carrierCode) { this.carrierCode = carrierCode; }

    public String getCarrierName() { return carrierName; }
    public void setCarrierName(String carrierName) { this.carrierName = carrierName; }

    public String getShipName() { return shipName; }
    public void setShipName(String shipName) { this.shipName = shipName; }

    public LocalDateTime getDepartureDate() { return departureDate; }
    public void setDepartureDate(LocalDateTime departureDate) { this.departureDate = departureDate; }

    public LocalDateTime getArrivalDate() { return arrivalDate; }
    public void setArrivalDate(LocalDateTime arrivalDate) { this.arrivalDate = arrivalDate; }

    public String getOriginUnlocode() { return originUnlocode; }
    public void setOriginUnlocode(String originUnlocode) { this.originUnlocode = originUnlocode; }

    public String getDestUnlocode() { return destUnlocode; }
    public void setDestUnlocode(String destUnlocode) { this.destUnlocode = destUnlocode; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public List<CarrierMovementProjection> getMovements() { return movements; }
    public void setMovements(List<CarrierMovementProjection> movements) { this.movements = movements; }

    public List<String> getAcceptedCargoTypes() { return acceptedCargoTypes; }
    public void setAcceptedCargoTypes(List<String> acceptedCargoTypes) { this.acceptedCargoTypes = acceptedCargoTypes; }

    public static class CarrierMovementProjection {
        private String departureUnlocode;
        private String arrivalUnlocode;
        private LocalDateTime departureTime;
        private LocalDateTime arrivalTime;

        public CarrierMovementProjection() { /* MyBatis result mapping */ }

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
