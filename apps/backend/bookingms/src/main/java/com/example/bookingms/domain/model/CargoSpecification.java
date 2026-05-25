package com.example.bookingms.domain.model;

import java.math.BigDecimal;

/**
 * 貨物仕様（種別・重量・寸法・個数・品名・固有情報）。値オブジェクト。
 *
 * <p>US04 で基本フィールドを追加、US05 で HAZARDOUS / REFRIGERATED 固有情報を追加。
 * Cargo Aggregate がコンストラクタ受信時に CargoType と固有情報の整合性を検証する。</p>
 *
 * @param cargoType 貨物種別（GENERAL / HAZARDOUS / REFRIGERATED）
 * @param weightKg 重量（kg）
 * @param dimensions 寸法
 * @param quantity 数量
 * @param productName 品名
 * @param hazardInfo 危険物申告情報（HAZARDOUS のみ必須、それ以外は null）
 * @param temperatureCondition 温度管理条件（REFRIGERATED のみ必須、それ以外は null）
 */
public record CargoSpecification(
        CargoType cargoType,
        BigDecimal weightKg,
        Dimensions dimensions,
        int quantity,
        String productName,
        HazardInfo hazardInfo,
        TemperatureCondition temperatureCondition
) {
    /**
     * US04 互換コンストラクタ（GENERAL 貨物、固有情報なし）。
     * 既存テストを壊さないために提供する。
     */
    public CargoSpecification(
            CargoType cargoType,
            BigDecimal weightKg,
            Dimensions dimensions,
            int quantity,
            String productName
    ) {
        this(cargoType, weightKg, dimensions, quantity, productName, null, null);
    }
}
