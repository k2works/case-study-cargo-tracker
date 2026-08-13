package com.example.cargotracker.routing.domain.model.valueobjects;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Routing Context が扱う貨物の重量。
 *
 * <p>Booking の {@code Weight} を参照しない理由は {@link RoutingBookingId} と同じである。
 *
 * @param kilograms 重量（キログラム）
 */
public record RoutingWeight(BigDecimal kilograms) {

    public RoutingWeight {
        if (kilograms == null) {
            throw new IllegalArgumentException("重量は必須です");
        }
        if (kilograms.signum() <= 0) {
            throw new IllegalArgumentException("重量は 0 より大きい値です: " + kilograms);
        }
    }

    public static RoutingWeight ofKilograms(BigDecimal kilograms) {
        return new RoutingWeight(kilograms);
    }

    /**
     * トン数。<strong>切り上げない。</strong> 概算費用の基礎になる値であり、
     * ここで丸めると 1kg の差が金額に跳ねる。
     */
    public BigDecimal tons() {
        return kilograms.divide(new BigDecimal("1000"), 6, RoundingMode.HALF_UP);
    }
}
