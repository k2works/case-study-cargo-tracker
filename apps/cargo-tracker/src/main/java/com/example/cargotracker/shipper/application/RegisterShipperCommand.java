package com.example.cargotracker.shipper.application;

import com.example.cargotracker.shipper.domain.CustomerCategory;

import java.math.BigDecimal;

public record RegisterShipperCommand(
        String name,
        String email,
        String phone,
        CustomerCategory category,
        String contractNumber,
        BigDecimal discountRate
) {
}
