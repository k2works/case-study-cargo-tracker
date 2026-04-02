package com.example.cargotracker.booking.domain.model.valueobjects;

import java.math.BigDecimal;
import java.util.Objects;

/**
 * 貨物仕様を表す値オブジェクト。
 */
public final class CargoSpecification {

    public record CargoDimensions(BigDecimal lengthCm, BigDecimal widthCm, BigDecimal heightCm) {
    }

    public record SpecialHandling(String unNumber, String hazardClass,
                                  BigDecimal minTempCelsius, BigDecimal maxTempCelsius) {
    }

    private final CargoType cargoType;
    private final BigDecimal weightKg;
    private final BigDecimal lengthCm;
    private final BigDecimal widthCm;
    private final BigDecimal heightCm;
    private final int quantity;
    private final String description;
    private final String unNumber;
    private final String hazardClass;
    private final BigDecimal minTempCelsius;
    private final BigDecimal maxTempCelsius;

    public CargoSpecification(CargoType cargoType, BigDecimal weightKg,
                               BigDecimal lengthCm, BigDecimal widthCm, BigDecimal heightCm,
                               int quantity, String description) {
        this(cargoType, weightKg, new CargoDimensions(lengthCm, widthCm, heightCm), quantity, description, null);
    }

    public CargoSpecification(CargoType cargoType, BigDecimal weightKg,
                               CargoDimensions dimensions,
                               int quantity, String description,
                               SpecialHandling specialHandling) {
        if (cargoType == null) throw new IllegalArgumentException("貨物種別は null にできません");
        if (weightKg == null) throw new IllegalArgumentException("重量は null にできません");
        if (weightKg.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("重量は 0 より大きくなければなりません");
        }
        if (quantity < 1) {
            throw new IllegalArgumentException("個数は 1 以上でなければなりません");
        }
        SpecialHandling handling = specialHandling == null ? new SpecialHandling(null, null, null, null) : specialHandling;
        CargoDimensions size = dimensions == null ? new CargoDimensions(null, null, null) : dimensions;
        if (cargoType == CargoType.DANGEROUS_GOODS && (handling.unNumber() == null || handling.unNumber().isBlank())) {
            throw new IllegalArgumentException("UN 番号は危険物の場合に必須です");
        }
        if (cargoType == CargoType.REFRIGERATED
                && (handling.minTempCelsius() == null || handling.maxTempCelsius() == null)) {
            throw new IllegalArgumentException("温度範囲は冷凍貨物の場合に必須です");
        }
        if (cargoType == CargoType.REFRIGERATED
                && handling.minTempCelsius().compareTo(handling.maxTempCelsius()) >= 0) {
            throw new IllegalArgumentException("最低温度は最高温度より低くなければなりません");
        }
        this.cargoType = cargoType;
        this.weightKg = weightKg;
        this.lengthCm = size.lengthCm();
        this.widthCm = size.widthCm();
        this.heightCm = size.heightCm();
        this.quantity = quantity;
        this.description = description;
        this.unNumber = handling.unNumber();
        this.hazardClass = handling.hazardClass();
        this.minTempCelsius = handling.minTempCelsius();
        this.maxTempCelsius = handling.maxTempCelsius();
    }

    public CargoType cargoType() { return cargoType; }
    public BigDecimal weightKg() { return weightKg; }
    public BigDecimal lengthCm() { return lengthCm; }
    public BigDecimal widthCm() { return widthCm; }
    public BigDecimal heightCm() { return heightCm; }
    public int quantity() { return quantity; }
    public String description() { return description; }
    public String unNumber() { return unNumber; }
    public String hazardClass() { return hazardClass; }
    public BigDecimal minTempCelsius() { return minTempCelsius; }
    public BigDecimal maxTempCelsius() { return maxTempCelsius; }

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
                && Objects.equals(description, that.description)
                && Objects.equals(unNumber, that.unNumber)
                && Objects.equals(hazardClass, that.hazardClass)
                && Objects.equals(minTempCelsius, that.minTempCelsius)
                && Objects.equals(maxTempCelsius, that.maxTempCelsius);
    }

    @Override
    public int hashCode() {
        return Objects.hash(cargoType, weightKg, lengthCm, widthCm, heightCm, quantity, description,
                unNumber, hazardClass, minTempCelsius, maxTempCelsius);
    }
}
