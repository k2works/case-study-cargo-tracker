package com.example.bookingms.interfaces.rest.dto;

import com.example.bookingms.domain.model.aggregates.Cargo;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 貨物レスポンス DTO
 */
public record CargoResponse(
        String bookingId,
        Long shipperId,
        String bookingStatus,
        String cargoType,
        BigDecimal weightKg,
        String originUnlocode,
        String destinationUnlocode,
        LocalDate arrivalDeadline
) {
    public static CargoResponse from(Cargo cargo) {
        String origin = null;
        String destination = null;
        LocalDate deadline = null;
        if (cargo.getRouteSpecification() != null) {
            origin = cargo.getRouteSpecification().getOriginUnlocode();
            destination = cargo.getRouteSpecification().getDestinationUnlocode();
            deadline = cargo.getRouteSpecification().getArrivalDeadline();
        }
        return new CargoResponse(
                cargo.getBookingId().getId(),
                cargo.getShipperId(),
                cargo.getBookingStatus().name(),
                cargo.getCargoType().name(),
                cargo.getWeight().getKg(),
                origin,
                destination,
                deadline
        );
    }
}
