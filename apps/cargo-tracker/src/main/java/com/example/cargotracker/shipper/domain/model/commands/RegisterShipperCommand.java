package com.example.cargotracker.shipper.domain.model.commands;

import com.example.cargotracker.shipper.domain.model.valueobjects.CustomerCategory;

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
