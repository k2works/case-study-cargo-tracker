package com.example.bookingms.domain.model;

import java.math.BigDecimal;

/**
 * 温度管理条件（US05、REFRIGERATED 貨物固有）。
 *
 * <p>輸送中に維持する最低温度・最高温度（摂氏）を保持する値オブジェクト。
 * Cargo Aggregate の不変条件で min <= max を検証する。</p>
 *
 * @param minCelsius 最低温度（摂氏、例: -25.0）
 * @param maxCelsius 最高温度（摂氏、例: -18.0）
 */
public record TemperatureCondition(
        BigDecimal minCelsius,
        BigDecimal maxCelsius
) {
}
