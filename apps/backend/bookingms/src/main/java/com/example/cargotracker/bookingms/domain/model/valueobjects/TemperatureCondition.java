package com.example.cargotracker.bookingms.domain.model.valueobjects;

import java.math.BigDecimal;
import java.util.Objects;

/**
 * 温度管理条件（{@link CargoType#REFRIGERATED} 時に必須）。
 *
 * <p>不変条件: {@code minCelsius <= maxCelsius}</p>
 */
public record TemperatureCondition(BigDecimal minCelsius, BigDecimal maxCelsius) {

    public TemperatureCondition {
        Objects.requireNonNull(minCelsius, "minCelsius");
        Objects.requireNonNull(maxCelsius, "maxCelsius");
        if (minCelsius.compareTo(maxCelsius) > 0) {
            throw new IllegalArgumentException("minCelsius は maxCelsius 以下である必要があります: min="
                    + minCelsius + " max=" + maxCelsius);
        }
    }
}
