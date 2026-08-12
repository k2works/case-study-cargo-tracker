package com.example.cargotracker.routing.domain.model;
import com.example.cargotracker.routing.domain.model.valueobjects.Money;
import com.example.cargotracker.routing.domain.model.valueobjects.RoutingCargoType;
import com.example.cargotracker.routing.domain.model.valueobjects.RoutingWeight;

import java.math.BigDecimal;

/**
 * 概算費用の算出（ADR-008）。
 *
 * <p><strong>実際の運賃ではない。</strong> 本システムは運賃表も港間の距離も持たない。
 * 材料が無いことを認めた上で、持っている値（重量・所要日数）から目安を出す。
 *
 * <p>単価と割増率は<strong>設定値として外から与える</strong>。ソースを変えずに
 * 調整できることが、この式が暫定であることの証拠になる。
 */
public final class FreightEstimator {

    private final BigDecimal ratePerTonDay;
    private final BigDecimal hazardousSurchargeRate;

    /**
     * @param ratePerTonDay          トン・日あたりの基準単価
     * @param hazardousSurchargeRate 危険物の割増率（1.5 なら 1.5 倍）
     */
    public FreightEstimator(BigDecimal ratePerTonDay, BigDecimal hazardousSurchargeRate) {
        if (ratePerTonDay == null || ratePerTonDay.signum() <= 0) {
            throw new IllegalArgumentException("基準単価は 0 より大きい値です");
        }
        if (hazardousSurchargeRate == null || hazardousSurchargeRate.signum() <= 0) {
            throw new IllegalArgumentException("割増率は 0 より大きい値です");
        }
        this.ratePerTonDay = ratePerTonDay;
        this.hazardousSurchargeRate = hazardousSurchargeRate;
    }

    /**
     * 概算費用。
     *
     * <p>基準単価 × 重量（トン） × 所要日数。危険物には割増を掛ける。
     * <strong>所要日数が 0 日でも 1 日として数える。</strong> 運んだ以上、
     * 費用が 0 になることはない。
     */
    public Money estimate(RoutingWeight weight, int transitDays, RoutingCargoType cargoType) {
        int days = Math.max(transitDays, 1);
        BigDecimal base = ratePerTonDay
                .multiply(weight.tons())
                .multiply(BigDecimal.valueOf(days));
        BigDecimal amount = cargoType == RoutingCargoType.HAZARDOUS
                ? base.multiply(hazardousSurchargeRate)
                : base;
        return Money.yen(amount);
    }
}
