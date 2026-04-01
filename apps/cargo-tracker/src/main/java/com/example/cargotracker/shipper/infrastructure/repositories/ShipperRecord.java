package com.example.cargotracker.shipper.infrastructure.repositories;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

public record ShipperRecord(
        UUID id,
        String name,
        String email,
        String phone,
        String address,
        String category,
        String contractNumber,
        BigDecimal discountRate,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
}
