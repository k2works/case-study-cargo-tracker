package com.example.cargotracker.booking.domain.model.valueobjects;

import java.math.BigDecimal;

/** 温度管理条件（℃）。冷凍・冷蔵の予約には必ず添える。 */
public record TemperatureRequirement(BigDecimal minCelsius, BigDecimal maxCelsius) {

    public TemperatureRequirement {
        if (minCelsius == null || maxCelsius == null) {
            throw new IllegalArgumentException("温度条件は下限と上限の両方が必要です");
        }
        if (minCelsius.compareTo(maxCelsius) > 0) {
            throw new IllegalArgumentException(
                    "温度条件の下限が上限を超えています: " + minCelsius + " 〜 " + maxCelsius);
        }
    }

    public static TemperatureRequirement of(String min, String max) {
        return new TemperatureRequirement(new BigDecimal(min), new BigDecimal(max));
    }
}
