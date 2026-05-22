package com.example.routingms.interfaces.rest.dto;

import com.example.routingms.domain.projections.VoyageProjection;
import com.example.routingms.domain.projections.VoyageProjection.CarrierMovementProjection;

import java.time.LocalDateTime;
import java.util.List;

public record VoyageResponse(
        String voyageNumber,
        String carrierCode,
        String carrierName,
        String shipName,
        String originUnlocode,
        String destUnlocode,
        LocalDateTime departureDate,
        LocalDateTime arrivalDate,
        String status,
        List<CarrierMovementResponse> movements,
        List<String> acceptedCargoTypes
) {
    public static VoyageResponse from(VoyageProjection p) {
        List<CarrierMovementResponse> movements = p.getMovements() == null ? null :
                p.getMovements().stream().map(CarrierMovementResponse::from).toList();
        return new VoyageResponse(
                p.getVoyageNumber(),
                p.getCarrierCode(),
                p.getCarrierName(),
                p.getShipName(),
                p.getOriginUnlocode(),
                p.getDestUnlocode(),
                p.getDepartureDate(),
                p.getArrivalDate(),
                p.getStatus(),
                movements,
                p.getAcceptedCargoTypes()
        );
    }

    public record CarrierMovementResponse(
            String departureUnlocode,
            String arrivalUnlocode,
            LocalDateTime departureTime,
            LocalDateTime arrivalTime
    ) {
        public static CarrierMovementResponse from(CarrierMovementProjection m) {
            return new CarrierMovementResponse(
                    m.getDepartureUnlocode(),
                    m.getArrivalUnlocode(),
                    m.getDepartureTime(),
                    m.getArrivalTime()
            );
        }
    }
}
