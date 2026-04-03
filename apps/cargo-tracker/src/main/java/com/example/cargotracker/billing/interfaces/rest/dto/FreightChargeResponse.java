package com.example.cargotracker.billing.interfaces.rest.dto;

import java.math.BigDecimal;

/**
 * 輸送料金レスポンス（REST DTO）。
 */
public record FreightChargeResponse(
        String id,
        String bookingId,
        String status,
        BigDecimal baseAmount,
        BigDecimal adjustmentAmount,
        BigDecimal totalAmount
) {
}
