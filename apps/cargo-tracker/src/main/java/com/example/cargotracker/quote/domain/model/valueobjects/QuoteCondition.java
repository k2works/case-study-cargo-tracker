package com.example.cargotracker.quote.domain.model.valueobjects;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Objects;

/**
 * 見積条件を表す値オブジェクト。
 */
public final class QuoteCondition {

    private final String originLocode;
    private final String destinationLocode;
    private final LocalDate requestedArrivalDate;
    private final CargoType cargoType;
    private final BigDecimal weightKg;

    public QuoteCondition(String originLocode, String destinationLocode,
                          LocalDate requestedArrivalDate, CargoType cargoType,
                          BigDecimal weightKg) {
        if (originLocode == null || originLocode.isBlank()) {
            throw new IllegalArgumentException("出発地 (UN/LOCODE) は null または空にできません");
        }
        if (destinationLocode == null || destinationLocode.isBlank()) {
            throw new IllegalArgumentException("目的地 (UN/LOCODE) は null または空にできません");
        }
        if (requestedArrivalDate == null) {
            throw new IllegalArgumentException("希望着日は null にできません");
        }
        if (cargoType == null) {
            throw new IllegalArgumentException("貨物種別は null にできません");
        }
        if (weightKg == null) {
            throw new IllegalArgumentException("重量は null にできません");
        }
        if (weightKg.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("重量は 0 より大きくなければなりません");
        }
        this.originLocode = originLocode;
        this.destinationLocode = destinationLocode;
        this.requestedArrivalDate = requestedArrivalDate;
        this.cargoType = cargoType;
        this.weightKg = weightKg;
    }

    public String originLocode() { return originLocode; }
    public String destinationLocode() { return destinationLocode; }
    public LocalDate requestedArrivalDate() { return requestedArrivalDate; }
    public CargoType cargoType() { return cargoType; }
    public BigDecimal weightKg() { return weightKg; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof QuoteCondition that)) return false;
        return Objects.equals(originLocode, that.originLocode)
                && Objects.equals(destinationLocode, that.destinationLocode)
                && Objects.equals(requestedArrivalDate, that.requestedArrivalDate)
                && cargoType == that.cargoType
                && weightKg.compareTo(that.weightKg) == 0;
    }

    @Override
    public int hashCode() {
        // weightKg は stripTrailingZeros で正規化してハッシュを一致させる
        return Objects.hash(originLocode, destinationLocode, requestedArrivalDate, cargoType,
                weightKg.stripTrailingZeros());
    }
}
