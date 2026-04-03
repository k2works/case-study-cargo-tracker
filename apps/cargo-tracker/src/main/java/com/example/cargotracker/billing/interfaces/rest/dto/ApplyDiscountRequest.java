package com.example.cargotracker.billing.interfaces.rest.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * 法人割引適用リクエスト（REST DTO）。
 */
public record ApplyDiscountRequest(
        @NotBlank String bookingId
) {
}
