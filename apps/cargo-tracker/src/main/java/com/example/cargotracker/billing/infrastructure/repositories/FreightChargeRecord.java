package com.example.cargotracker.billing.infrastructure.repositories;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * freight_charges テーブルのレコード。
 */
public record FreightChargeRecord(
        String id,
        String bookingId,
        String status,
        BigDecimal baseAmount,
        BigDecimal adjustmentAmount,
        BigDecimal totalAmount,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {}
