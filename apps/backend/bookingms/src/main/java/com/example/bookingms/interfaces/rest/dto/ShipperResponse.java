package com.example.bookingms.interfaces.rest.dto;

import com.example.bookingms.domain.model.aggregates.Shipper;

import java.math.BigDecimal;

public record ShipperResponse(
        Long id,
        String shipperCode,
        String shipperType,
        String name,
        String email,
        String phone,
        String contractNumber,
        BigDecimal discountRate
) {
    public static ShipperResponse from(Shipper shipper) {
        return new ShipperResponse(
                shipper.getId(),
                shipper.getShipperCode(),
                shipper.getShipperType().name(),
                shipper.getName(),
                shipper.getEmail(),
                shipper.getPhone(),
                shipper.getContractNumber(),
                shipper.getDiscountRate()
        );
    }
}
