package com.example.cargotracker.billing.domain.model.valueobjects;

import com.example.cargotracker.shared.domain.error.BusinessRuleViolation;
import com.example.cargotracker.shared.domain.location.UnLocode;
import java.math.BigDecimal;
import java.util.Map;

/**
 * 料率表（正典の料金計算）。
 *
 * <p><b>料率をハードコードしない。</b> 値は {@code application.yml} から読む
 * （{@code RateTableProperties}）。見積（US01・IT14）も同じ値を読むので、出典が
 * 1 つでなければ片方だけ直る（[ADR-0016]）。</p>
 *
 * <p><b>欠けていれば組み立ての時点で断る。</b> 請求のたびに落ちるより、起動して
 * すぐ分かるほうがよい。</p>
 *
 * @param baseFare 基準運賃（1 区間・1,000kg・一般貨物）
 * @param regionFactors 地域係数（国内 1.0 / 近海 2.5 / 遠洋 6.0）
 * @param cargoTypeFactors 貨物種別係数（GENERAL 1.0 / HAZARDOUS 1.8 / REFRIGERATED 1.5）
 * @param countryRegions 国コード → 地域区分。<b>表に無い国は遠洋</b>として数える
 * @param taxRate 消費税率（輸出免税の判定は {@code FreightChargeCalculator}）
 */
public record RateTable(
        Money baseFare,
        Map<PortRegion, BigDecimal> regionFactors,
        Map<String, BigDecimal> cargoTypeFactors,
        Map<String, PortRegion> countryRegions,
        BigDecimal taxRate) {

    public RateTable {
        if (baseFare == null) {
            throw new BusinessRuleViolation("基準運賃が設定されていません（billing.rates.base-fare）");
        }
        if (regionFactors == null || regionFactors.size() < PortRegion.values().length) {
            throw new BusinessRuleViolation(
                    "地域係数が足りません（billing.rates.region-factors）: " + regionFactors);
        }
        if (cargoTypeFactors == null || cargoTypeFactors.isEmpty()) {
            throw new BusinessRuleViolation("貨物種別係数が設定されていません");
        }
        if (taxRate == null || taxRate.signum() < 0) {
            throw new BusinessRuleViolation("消費税率が設定されていません: " + taxRate);
        }
        regionFactors = Map.copyOf(regionFactors);
        cargoTypeFactors = Map.copyOf(cargoTypeFactors);
        countryRegions = countryRegions == null ? Map.of() : Map.copyOf(countryRegions);
    }

    public BigDecimal regionFactor(PortRegion region) {
        BigDecimal factor = regionFactors.get(region);
        if (factor == null) {
            throw new BusinessRuleViolation("地域係数が設定されていません: " + region);
        }
        return factor;
    }

    /**
     * 貨物種別の係数。
     *
     * <p><b>知らない種別は断る。</b> 黙って 1.0 で通すと、危険物が一般貨物の料金で
     * 請求される（名簿方式の検査が「載っていないものを通す」のと同じ形）。</p>
     */
    public BigDecimal cargoTypeFactor(String cargoType) {
        BigDecimal factor = cargoTypeFactors.get(cargoType);
        if (factor == null) {
            throw new BusinessRuleViolation("料率表に無い貨物種別です: " + cargoType);
        }
        return factor;
    }

    /**
     * 港の地域区分。<b>表に無い国は遠洋</b>として数える。
     *
     * <p>安いほうに倒さないのは、取りこぼした請求はあとから取り返せないためである
     * （督促を早く点けるのと同じ考え方）。</p>
     */
    public PortRegion regionOf(UnLocode port) {
        return countryRegions.getOrDefault(port.countryCode().value(), PortRegion.OCEAN);
    }

    /** 区間の地域区分は<b>両端の重いほう</b>（正典）。 */
    public PortRegion regionOfLeg(UnLocode from, UnLocode to) {
        return regionOf(from).heavier(regionOf(to));
    }
}
