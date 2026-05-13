package com.example.cargotracker.bookingms.domain.model.valueobjects;

import java.math.BigDecimal;
import java.util.Objects;

/**
 * 貨物の仕様（種別・重量・寸法・個数・品名 + 危険物/冷凍貨物の付加情報）。
 *
 * <p>不変条件:</p>
 * <ul>
 *   <li>{@link CargoType#HAZARDOUS} の場合は {@link #hazardInfo} 必須</li>
 *   <li>{@link CargoType#REFRIGERATED} の場合は {@link #temperatureCondition} 必須</li>
 *   <li>{@link CargoType#GENERAL} の場合は両方 null</li>
 * </ul>
 */
public record CargoSpecification(
        CargoType cargoType,
        BigDecimal weightKg,
        Dimensions dimensions,
        int quantity,
        String productName,
        HazardInfo hazardInfo,
        TemperatureCondition temperatureCondition) {

    public CargoSpecification {
        Objects.requireNonNull(cargoType, "cargoType");
        Objects.requireNonNull(weightKg, "weightKg");
        Objects.requireNonNull(productName, "productName");
        if (weightKg.signum() <= 0) {
            throw new IllegalArgumentException("weightKg は 0 より大きい必要があります: " + weightKg);
        }
        if (quantity <= 0) {
            throw new IllegalArgumentException("quantity は 1 以上である必要があります: " + quantity);
        }
        if (productName.isBlank()) {
            throw new IllegalArgumentException("productName は必須です");
        }
        if (cargoType == CargoType.HAZARDOUS && hazardInfo == null) {
            throw new IllegalArgumentException("HAZARDOUS は HazardInfo が必須です");
        }
        if (cargoType == CargoType.REFRIGERATED && temperatureCondition == null) {
            throw new IllegalArgumentException("REFRIGERATED は TemperatureCondition が必須です");
        }
        if (cargoType == CargoType.GENERAL && (hazardInfo != null || temperatureCondition != null)) {
            throw new IllegalArgumentException("GENERAL は HazardInfo / TemperatureCondition を持てません");
        }
    }

    /** 一般貨物のファクトリ。 */
    public static CargoSpecification general(BigDecimal weightKg, Dimensions dimensions,
                                             int quantity, String productName) {
        return new CargoSpecification(CargoType.GENERAL, weightKg, dimensions, quantity, productName, null, null);
    }
}
