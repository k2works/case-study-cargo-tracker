package com.example.cargotracker.booking.domain.model.valueobjects;

import java.math.BigDecimal;

public record TemperatureRequirement(BigDecimal minTemperature, BigDecimal maxTemperature, TemperatureUnit unit) {
    public TemperatureRequirement {
        if (minTemperature == null) {
            throw new IllegalArgumentException("minTemperature must not be null");
        }
        if (maxTemperature == null) {
            throw new IllegalArgumentException("maxTemperature must not be null");
        }
        if (unit == null) {
            throw new IllegalArgumentException("unit must not be null");
        }
        if (minTemperature.compareTo(maxTemperature) > 0) {
            throw new IllegalArgumentException("minTemperature must not exceed maxTemperature");
        }
    }
}
