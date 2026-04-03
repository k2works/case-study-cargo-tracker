package com.example.cargotracker.billing.infrastructure.repositories;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

public record InvoiceRecord(
        String id,
        String bookingId,
        String freightChargeId,
        BigDecimal amount,
        LocalDate dueDate,
        String paymentStatus,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {}
