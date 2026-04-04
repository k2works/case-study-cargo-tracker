package com.example.cargotracker.routing.domain.model;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/**
 * 経路設計条件を表す Read Model。
 *
 * <p>予約情報から取得した経路設計に必要な条件を保持する。
 * 全フィールドが揃っているかどうかは {@link #isComplete()} で確認できる。
 */
public record RouteDesignCondition(
    UUID bookingId,
    String originLocode,
    String destinationLocode,
    LocalDate requestedArrivalDate,
    CargoType cargoType,
    BigDecimal weightKg
) {
    public RouteDesignCondition {
        if (bookingId == null) {
            throw new IllegalArgumentException("bookingId は null にできません");
        }
    }

    /**
     * 経路設計に必要な全条件が揃っているかを判定する。
     *
     * @return 全フィールドが有効な値であれば {@code true}
     */
    public boolean isComplete() {
        return originLocode != null && !originLocode.isBlank()
            && destinationLocode != null && !destinationLocode.isBlank()
            && requestedArrivalDate != null
            && cargoType != null
            && weightKg != null && weightKg.compareTo(BigDecimal.ZERO) > 0;
    }
}
