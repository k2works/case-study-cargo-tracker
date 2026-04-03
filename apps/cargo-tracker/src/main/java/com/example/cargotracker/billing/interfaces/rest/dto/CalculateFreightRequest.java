package com.example.cargotracker.billing.interfaces.rest.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * 輸送料金算出リクエスト（REST DTO）。
 */
public record CalculateFreightRequest(@NotBlank String bookingId) {
}
