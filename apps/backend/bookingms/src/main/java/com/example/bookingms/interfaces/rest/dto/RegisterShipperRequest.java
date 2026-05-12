package com.example.bookingms.interfaces.rest.dto;

import java.math.BigDecimal;

public record RegisterShipperRequest(
        String name,
        String email,
        String phone,
        String shipperType,
        String contractNumber,
        BigDecimal discountRate
) {}
