package com.example.bookingms.domain.model;

import java.math.BigDecimal;

/**
 * 貨物仕様（種別・重量・寸法・個数・品名）。値オブジェクト。
 *
 * <p>US05 で HAZARDOUS / REFRIGERATED 固有情報を追加予定。</p>
 */
public record CargoSpecification(
        CargoType cargoType,
        BigDecimal weightKg,
        Dimensions dimensions,
        int quantity,
        String productName
) {
}
