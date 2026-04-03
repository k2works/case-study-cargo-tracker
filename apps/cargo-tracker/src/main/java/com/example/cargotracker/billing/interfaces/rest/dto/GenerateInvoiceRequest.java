package com.example.cargotracker.billing.interfaces.rest.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * 精算書発行リクエスト（REST DTO）。
 */
public record GenerateInvoiceRequest(
        @NotBlank String bookingId,
        @NotBlank String freightChargeId
) {
}
