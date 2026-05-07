package com.example.bookingms.interfaces.rest.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 貨物予約登録リクエスト DTO
 */
public record CreateCargoRequest(
        Long shipperId,
        String cargoType,
        BigDecimal weightKg,
        String originUnlocode,
        String destinationUnlocode,
        LocalDate arrivalDeadline
) {
}
