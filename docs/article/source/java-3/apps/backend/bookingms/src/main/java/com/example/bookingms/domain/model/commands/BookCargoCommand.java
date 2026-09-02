package com.example.bookingms.domain.model.commands;

import com.example.bookingms.domain.model.valueobjects.CargoType;
import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 貨物予約の登録要求。
 *
 * <p>地点は UN/LOCODE のまま受け取り、実在の確認と業務タイムゾーンの解決はユースケースが行う。
 * ここで {@code Location} に変換すると、存在しない地点コードが「名称不明の地点」として通る。
 */
public record BookCargoCommand(
        Long shipperId,
        CargoType type,
        BigDecimal weightKg,
        Integer quantity,
        String description,
        BigDecimal lengthCm,
        BigDecimal widthCm,
        BigDecimal heightCm,
        String originUnLocode,
        String destinationUnLocode,
        LocalDate departureDate,
        LocalDate arrivalDeadline,
        String hazardousClass,
        String unNumber,
        String properShippingName,
        BigDecimal minCelsius,
        BigDecimal maxCelsius) {
}
