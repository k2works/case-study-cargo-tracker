package com.example.bookingms.domain.model.valueobjects;

import java.math.BigDecimal;

/**
 * 貨物の中身の仕様。
 *
 * <p>{@code Cargo.book(...)} の引数が 7 個を超えたため、まとめて渡す。引数の羅列は
 * 順番を取り違えても型が合ってしまい、重量と個数が入れ替わったまま通る。
 *
 * @param type 貨物種別
 * @param weightKg 重量（kg）
 * @param quantity 個数（任意）
 * @param description 品名（任意）
 * @param dimensions 外寸（任意）
 * @param hazardousDeclaration 危険物申告（危険物のときだけ）
 * @param temperatureRequirement 温度条件（冷凍・冷蔵のときだけ）
 */
public record CargoSpecification(
        CargoType type,
        BigDecimal weightKg,
        Integer quantity,
        String description,
        Dimensions dimensions,
        HazardousDeclaration hazardousDeclaration,
        TemperatureRequirement temperatureRequirement) {

    /** 追加情報を持たない一般貨物。 */
    public static CargoSpecification general(BigDecimal weightKg, Integer quantity,
            String description, Dimensions dimensions) {
        return new CargoSpecification(
                CargoType.GENERAL, weightKg, quantity, description, dimensions, null, null);
    }
}
