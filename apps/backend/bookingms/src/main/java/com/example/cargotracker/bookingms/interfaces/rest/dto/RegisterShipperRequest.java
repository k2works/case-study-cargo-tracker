package com.example.cargotracker.bookingms.interfaces.rest.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

import java.math.BigDecimal;

public record RegisterShipperRequest(
        @NotBlank String name,
        @NotBlank @Email String email,
        String phone,
        @NotBlank String shipperType,
        String contractNumber,
        BigDecimal discountRate
) {}
