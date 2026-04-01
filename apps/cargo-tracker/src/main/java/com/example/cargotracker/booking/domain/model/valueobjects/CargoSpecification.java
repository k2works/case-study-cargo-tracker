package com.example.cargotracker.booking.domain.model.valueobjects;

import java.math.BigDecimal;
import java.util.Objects;

/**
 * 貨物仕様を表す値オブジェクト。
 */
public final class CargoSpecification {

    private final CargoType cargoType;
    private final BigDecimal weightKg;
    private final BigDecimal lengthCm;
    private final BigDecimal widthCm;
    private final BigDecimal heightCm;
    private final int quantity;
    private final String description;

    public CargoSpecification(CargoType cargoType, BigDecimal weightKg,
                               BigDecimal lengthCm, BigDecimal widthCm, BigDecimal heightCm,
                               int quantity, String description) {
        if (cargoType == null) throw new IllegalArgumentException("貨物種別は null にできません");
        if (weightKg == null) throw new IllegalArgumentException("重量は null にできません");
        if (weightKg.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("重量は 0 より大きくなければなりません");
        }
        if (quantity < 1) {
            throw new IllegalArgumentException("個数は 1 以上でなければなりません");
        }
        this.cargoType = cargoType;
        this.weightKg = weightKg;
        this.lengthCm = lengthCm;
        this.widthCm = widthCm;
        this.heightCm = heightCm;
        this.quantity = quantity;
        this.description = description;
    }

    public CargoType cargoType() { return cargoType; }
    public BigDecimal weightKg() { return weightKg; }
    public BigDecimal lengthCm() { return lengthCm; }
    public BigDecimal widthCm() { return widthCm; }
    public BigDecimal heightCm() { return heightCm; }
    public int quantity() { return quantity; }
    public String description() { return description; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof CargoSpecification that)) return false;
        return quantity == that.quantity
                && cargoType == that.cargoType
                && Objects.equals(weightKg, that.weightKg)
                && Objects.equals(lengthCm, that.lengthCm)
                && Objects.equals(widthCm, that.widthCm)
                && Objects.equals(heightCm, that.heightCm)
                && Objects.equals(description, that.description);
    }

    @Override
    public int hashCode() {
        return Objects.hash(cargoType, weightKg, lengthCm, widthCm, heightCm, quantity, description);
    }
}
