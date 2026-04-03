package com.example.cargotracker.billing.interfaces.rest.dto;

import jakarta.validation.constraints.NotBlank;

import java.math.BigDecimal;

/**
 * 輸送料金算出リクエスト（REST DTO）。
 */
public record CalculateFreightRequest(@NotBlank String bookingId, BigDecimal adjustmentAmount) {
}
