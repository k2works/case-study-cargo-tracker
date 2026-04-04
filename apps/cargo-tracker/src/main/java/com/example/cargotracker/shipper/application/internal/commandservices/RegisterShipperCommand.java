package com.example.cargotracker.shipper.application.internal.commandservices;

import com.example.cargotracker.shipper.domain.model.aggregates.ShipperType;

import java.math.BigDecimal;

public record RegisterShipperCommand(
        String code,
        String name,
        String email,
        String phone,
        ShipperType shipperType,
        String contractNumber,
        BigDecimal discountRate
) {
}
