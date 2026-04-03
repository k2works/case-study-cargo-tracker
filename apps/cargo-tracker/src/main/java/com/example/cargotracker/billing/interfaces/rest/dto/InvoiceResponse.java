package com.example.cargotracker.billing.interfaces.rest.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 精算書レスポンス（REST DTO）。
 */
public record InvoiceResponse(
        String id,
        String bookingId,
        String freightChargeId,
        BigDecimal amount,
        LocalDate dueDate,
        String paymentStatus
) {
}
