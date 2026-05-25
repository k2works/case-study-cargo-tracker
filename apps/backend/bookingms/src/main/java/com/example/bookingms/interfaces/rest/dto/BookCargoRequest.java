package com.example.bookingms.interfaces.rest.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 貨物予約登録リクエスト DTO（US04、POST /api/v1/bookings）。
 *
 * <p>{@code bookingId} は省略可能で、省略時はサーバー側で UUID を採番する。</p>
 */
public record BookCargoRequest(
        String bookingId,
        String shipperId,
        String originUnlocode,
        String destinationUnlocode,
        LocalDate arrivalDeadline,
        String cargoType,
        BigDecimal weightKg,
        Integer lengthCm,
        Integer widthCm,
        Integer heightCm,
        Integer quantity,
        String productName
) {
}
