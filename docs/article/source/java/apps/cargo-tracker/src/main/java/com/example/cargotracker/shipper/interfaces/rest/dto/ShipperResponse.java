package com.example.cargotracker.shipper.interfaces.rest.dto;

import java.math.BigDecimal;

public record ShipperResponse(
        String id,
        String code,
        String name,
        String email,
        String phone,
        String address,
        String shipperType,
        String contractNumber,
        BigDecimal discountRate
) {}
