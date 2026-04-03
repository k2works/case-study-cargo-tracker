package com.example.cargotracker.exception.infrastructure.repositories;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * cargo_exceptions テーブルのレコード。
 */
public record CargoExceptionRecord(
        String id,
        String trackingNumber,
        String exceptionType,
        String locationCode,
        LocalDateTime occurredAt,
        String reason,
        Boolean urgent,
        String resolution,
        LocalDate estimatedArrivalDate,
        LocalDateTime createdAt
) {}
