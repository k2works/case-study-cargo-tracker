package com.example.bookingms.domain.model;

import java.math.BigDecimal;
import java.util.Objects;

/**
 * 温度管理条件（US05）。
 *
 * <p>下限と上限は対で意味を持つ。下限が上限を超えた条件は「満たせる温度が存在しない」ことを
 * 表しており、荷役で必ず破られる。受け入れた時点で拒む。
 */
public final class TemperatureRequirement {

    private final BigDecimal minCelsius;
    private final BigDecimal maxCelsius;

    private TemperatureRequirement(BigDecimal minCelsius, BigDecimal maxCelsius) {
        this.minCelsius = minCelsius;
        this.maxCelsius = maxCelsius;
    }

    public static TemperatureRequirement of(BigDecimal minCelsius, BigDecimal maxCelsius) {
        if (minCelsius == null || maxCelsius == null) {
            throw new IllegalArgumentException("保管温度の下限と上限はどちらも必須です");
        }
        if (minCelsius.compareTo(maxCelsius) > 0) {
            throw new IllegalArgumentException(
                    "保管温度の下限が上限を超えています: %s > %s".formatted(minCelsius, maxCelsius));
        }
        return new TemperatureRequirement(minCelsius, maxCelsius);
    }

    /** 永続化された行から復元する。ここでは検査しない。 */
    public static TemperatureRequirement restore(BigDecimal minCelsius, BigDecimal maxCelsius) {
        return new TemperatureRequirement(minCelsius, maxCelsius);
    }

    public BigDecimal minCelsius() {
        return minCelsius;
    }

    public BigDecimal maxCelsius() {
        return maxCelsius;
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof TemperatureRequirement requirement
                && minCelsius.compareTo(requirement.minCelsius) == 0
                && maxCelsius.compareTo(requirement.maxCelsius) == 0;
    }

    @Override
    public int hashCode() {
        return Objects.hash(minCelsius.stripTrailingZeros(), maxCelsius.stripTrailingZeros());
    }

    @Override
    public String toString() {
        return "%s〜%s℃".formatted(minCelsius, maxCelsius);
    }
}
