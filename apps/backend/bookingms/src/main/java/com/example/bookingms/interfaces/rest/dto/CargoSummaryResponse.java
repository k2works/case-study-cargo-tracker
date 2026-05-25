package com.example.bookingms.interfaces.rest.dto;

import com.example.bookingms.domain.projections.CargoSummary;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 貨物予約応答 DTO（US04 + US05、GET /api/v1/bookings 系）。
 *
 * <p>US05 で hazard_* / temperature_* フィールドを追加。</p>
 */
public record CargoSummaryResponse(
        String bookingId,
        String shipperId,
        String trackingNumber,
        String originUnlocode,
        String destinationUnlocode,
        LocalDate arrivalDeadline,
        String cargoType,
        BigDecimal weightKg,
        Integer lengthCm,
        Integer widthCm,
        Integer heightCm,
        Integer quantity,
        String productName,
        String hazardImoClass,
        String hazardUnNumber,
        String hazardDeclaration,
        BigDecimal temperatureMinC,
        BigDecimal temperatureMaxC,
        String bookingStatus,
        String routingStatus,
        BigDecimal estimatedAmount,
        String estimatedCurrency
) {
    public static CargoSummaryResponse from(CargoSummary p) {
        return new CargoSummaryResponse(
                p.getBookingId(),
                p.getShipperId(),
                p.getTrackingNumber(),
                p.getOriginUnlocode(),
                p.getDestinationUnlocode(),
                p.getArrivalDeadline(),
                p.getCargoType(),
                p.getWeightKg(),
                p.getLengthCm(),
                p.getWidthCm(),
                p.getHeightCm(),
                p.getQuantity(),
                p.getProductName(),
                p.getHazardImoClass(),
                p.getHazardUnNumber(),
                p.getHazardDeclaration(),
                p.getTemperatureMinC(),
                p.getTemperatureMaxC(),
                p.getBookingStatus(),
                p.getRoutingStatus(),
                p.getEstimatedAmount(),
                p.getEstimatedCurrency()
        );
    }
}
