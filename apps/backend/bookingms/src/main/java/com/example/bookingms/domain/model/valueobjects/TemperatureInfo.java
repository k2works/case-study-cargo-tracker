package com.example.bookingms.domain.model.valueobjects;

/**
 * 温度管理条件
 */
public record TemperatureInfo(
        double minTemperature,
        double maxTemperature,
        String unit
) {
    public TemperatureInfo {
        if (minTemperature > maxTemperature) {
            throw new IllegalArgumentException("最低温度は最高温度以下である必要があります");
        }
        if (unit == null || unit.isBlank()) {
            throw new IllegalArgumentException("温度単位は必須です");
        }
    }
}
