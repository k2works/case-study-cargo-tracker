package com.example.cargotracker.shipper.interfaces.rest.dto;

import com.example.cargotracker.shipper.domain.model.aggregates.Shipper;

import java.math.BigDecimal;

public record ShipperResponse(
        String id,
        String name,
        String email,
        String phone,
        String category,
        String contractNumber,
        BigDecimal discountRate
) {
    public static ShipperResponse from(Shipper shipper) {
        return new ShipperResponse(
                shipper.getId().toString(),
                shipper.getName().value(),
                shipper.getContactInfo().email(),
                shipper.getContactInfo().phone(),
                shipper.getCategory().name(),
                shipper.getCorporateContractInfo() != null ? shipper.getCorporateContractInfo().contractNumber() : null,
                shipper.getCorporateContractInfo() != null ? shipper.getCorporateContractInfo().discountRate() : null
        );
    }
}
