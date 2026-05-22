package com.example.routingms.interfaces.rest.dto;

import com.example.routingms.domain.projections.VoyageProjection;
import com.example.routingms.domain.projections.VoyageProjection.CarrierMovementProjection;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

public class VoyageResponse {

    private String voyageNumber;
    private String carrierCode;
    private String carrierName;
    private String shipName;
    private String originUnlocode;
    private String destUnlocode;
    private LocalDateTime departureDate;
    private LocalDateTime arrivalDate;
    private String status;
    private List<CarrierMovementResponse> movements;
    private List<String> acceptedCargoTypes;

    public static VoyageResponse from(VoyageProjection p) {
        VoyageResponse r = new VoyageResponse();
        r.voyageNumber = p.getVoyageNumber();
        r.carrierCode = p.getCarrierCode();
        r.carrierName = p.getCarrierName();
        r.shipName = p.getShipName();
        r.originUnlocode = p.getOriginUnlocode();
        r.destUnlocode = p.getDestUnlocode();
        r.departureDate = p.getDepartureDate();
        r.arrivalDate = p.getArrivalDate();
        r.status = p.getStatus();
        r.acceptedCargoTypes = p.getAcceptedCargoTypes();
        if (p.getMovements() != null) {
            r.movements = p.getMovements().stream()
                    .map(CarrierMovementResponse::from)
                    .collect(Collectors.toList());
        }
        return r;
    }

    public String getVoyageNumber() { return voyageNumber; }
    public String getCarrierCode() { return carrierCode; }
    public String getCarrierName() { return carrierName; }
    public String getShipName() { return shipName; }
    public String getOriginUnlocode() { return originUnlocode; }
    public String getDestUnlocode() { return destUnlocode; }
    public LocalDateTime getDepartureDate() { return departureDate; }
    public LocalDateTime getArrivalDate() { return arrivalDate; }
    public String getStatus() { return status; }
    public List<CarrierMovementResponse> getMovements() { return movements; }
    public List<String> getAcceptedCargoTypes() { return acceptedCargoTypes; }

    public static class CarrierMovementResponse {
        private String departureUnlocode;
        private String arrivalUnlocode;
        private LocalDateTime departureTime;
        private LocalDateTime arrivalTime;

        public static CarrierMovementResponse from(CarrierMovementProjection m) {
            CarrierMovementResponse r = new CarrierMovementResponse();
            r.departureUnlocode = m.getDepartureUnlocode();
            r.arrivalUnlocode = m.getArrivalUnlocode();
            r.departureTime = m.getDepartureTime();
            r.arrivalTime = m.getArrivalTime();
            return r;
        }

        public String getDepartureUnlocode() { return departureUnlocode; }
        public String getArrivalUnlocode() { return arrivalUnlocode; }
        public LocalDateTime getDepartureTime() { return departureTime; }
        public LocalDateTime getArrivalTime() { return arrivalTime; }
    }
}
