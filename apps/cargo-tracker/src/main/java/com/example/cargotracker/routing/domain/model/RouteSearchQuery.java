package com.example.cargotracker.routing.domain.model;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * ルート検索の入力条件を表す Read Model。
 */
public record RouteSearchQuery(
    String originLocode,
    String destinationLocode,
    LocalDate requestedArrivalDate,
    CargoType cargoType,
    BigDecimal weightKg
) {
    public RouteSearchQuery {
        if (originLocode == null || originLocode.isBlank()) {
            throw new IllegalArgumentException("出発地 LOCODE は null または空にできません");
        }
        if (destinationLocode == null || destinationLocode.isBlank()) {
            throw new IllegalArgumentException("目的地 LOCODE は null または空にできません");
        }
        if (requestedArrivalDate == null) {
            throw new IllegalArgumentException("希望着日は null にできません");
        }
        if (cargoType == null) {
            throw new IllegalArgumentException("貨物種別は null にできません");
        }
        if (weightKg == null || weightKg.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("重量は正の値でなければなりません");
        }
    }
}
