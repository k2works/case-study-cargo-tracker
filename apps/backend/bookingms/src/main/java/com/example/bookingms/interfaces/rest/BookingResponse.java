package com.example.bookingms.interfaces.rest;

import com.example.bookingms.domain.model.BookingId;
import com.example.bookingms.domain.model.Cargo;
import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 貨物予約の応答。
 *
 * <p>予約金額は返さない。IT2 では算出できず（US18・IT11）、0 を返すと未算出と無料が
 * 区別できなくなる（ADR-009）。
 */
public record BookingResponse(
        Long id,
        String bookingId,
        Long shipperId,
        String bookingStatus,
        String transportStatus,
        String routingStatus,
        String type,
        BigDecimal weightKg,
        Integer quantity,
        String description,
        BigDecimal lengthCm,
        BigDecimal widthCm,
        BigDecimal heightCm,
        String originUnLocode,
        String originName,
        String destinationUnLocode,
        String destinationName,
        LocalDate departureDate,
        LocalDate arrivalDeadline,
        String hazardousClass,
        String unNumber,
        String properShippingName,
        BigDecimal minCelsius,
        BigDecimal maxCelsius) {

    public static BookingResponse from(Cargo cargo) {
        var specification = cargo.specification();
        var route = cargo.routeSpecification();
        var dimensions = specification.dimensions();

        return new BookingResponse(
                cargo.id(),
                cargo.bookingId().map(BookingId::value).orElse(null),
                cargo.shipperId(),
                cargo.bookingStatus().name(),
                cargo.transportStatus().name(),
                cargo.routingStatus().name(),
                specification.type().name(),
                specification.weightKg(),
                specification.quantity(),
                specification.description(),
                dimensions == null ? null : dimensions.lengthCm(),
                dimensions == null ? null : dimensions.widthCm(),
                dimensions == null ? null : dimensions.heightCm(),
                route.origin().unLocode(),
                route.origin().name(),
                route.destination().unLocode(),
                route.destination().name(),
                route.departureDate().orElse(null),
                route.arrivalDeadline(),
                cargo.hazardousDeclaration().map(d -> d.hazardousClass()).orElse(null),
                cargo.hazardousDeclaration().map(d -> d.unNumber()).orElse(null),
                cargo.hazardousDeclaration().map(d -> d.properShippingName()).orElse(null),
                cargo.temperatureRequirement().map(t -> t.minCelsius()).orElse(null),
                cargo.temperatureRequirement().map(t -> t.maxCelsius()).orElse(null));
    }
}
