package com.example.bookingms.interfaces.rest;

import com.example.bookingms.domain.model.valueobjects.CargoType;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 貨物予約の登録要求。
 *
 * <p>必須の判定は最小限にとどめ、業務の規則（危険物なら申告が必須など）は集約に任せる。
 * ここで重ねて書くと、規則が 2 箇所に増えて片方だけが直る。
 */
public record BookingRequest(
        @NotNull Long shipperId,
        @NotNull CargoType type,
        @NotNull BigDecimal weightKg,
        Integer quantity,
        String description,
        BigDecimal lengthCm,
        BigDecimal widthCm,
        BigDecimal heightCm,
        @NotNull String originUnLocode,
        @NotNull String destinationUnLocode,
        LocalDate departureDate,
        @NotNull LocalDate arrivalDeadline,
        String hazardousClass,
        String unNumber,
        String properShippingName,
        BigDecimal minCelsius,
        BigDecimal maxCelsius) {
}
