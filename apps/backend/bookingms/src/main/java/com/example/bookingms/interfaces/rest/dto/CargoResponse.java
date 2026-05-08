package com.example.bookingms.interfaces.rest.dto;

import com.example.bookingms.domain.model.aggregates.Cargo;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

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
        LocalDate arrivalDeadline,
        List<LegResponse> legs
) {
    /**
     * 旅程区間レスポンス DTO
     */
    public record LegResponse(
            String voyageNumber,
            String loadLocationUnlocode,
            String unloadLocationUnlocode,
            LocalDateTime loadTime,
            LocalDateTime unloadTime
    ) {}

    public static CargoResponse from(Cargo cargo) {
        String origin = null;
        String destination = null;
        LocalDate deadline = null;
        if (cargo.getRouteSpecification() != null) {
            origin = cargo.getRouteSpecification().getOriginUnlocode();
            destination = cargo.getRouteSpecification().getDestinationUnlocode();
            deadline = cargo.getRouteSpecification().getArrivalDeadline();
        }

        List<LegResponse> legs = List.of();
        if (cargo.getCargoItinerary() != null) {
            legs = cargo.getCargoItinerary().getLegs().stream()
                    .map(l -> new LegResponse(
                            l.getVoyageNumber(),
                            l.getLoadLocationUnlocode(),
                            l.getUnloadLocationUnlocode(),
                            l.getLoadTime(),
                            l.getUnloadTime()))
                    .toList();
        }

        return new CargoResponse(
                cargo.getBookingId().getId(),
                cargo.getShipperId(),
                cargo.getBookingStatus().name(),
                cargo.getCargoType().name(),
                cargo.getWeight().getKg(),
                origin,
                destination,
                deadline,
                legs
        );
    }
}
