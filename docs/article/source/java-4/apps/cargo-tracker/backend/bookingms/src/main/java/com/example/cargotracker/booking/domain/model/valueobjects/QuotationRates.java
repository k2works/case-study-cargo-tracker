package com.example.cargotracker.booking.domain.model.valueobjects;

import com.example.cargotracker.shared.domain.error.BusinessRuleViolation;
import com.example.cargotracker.shared.domain.location.UnLocode;
import java.math.BigDecimal;
import java.util.Map;

/**
 * 見積が読む料率（US01・[ADR-0016]）。
 *
 * <p><b>billingms の {@code RateTable} とは別の型である。</b> BC をまたいで型は
 * 共有しない（{@code domain-model.md}「置かないもの」）。共有するのは<b>設定
 * ファイルそのもの</b>（{@code shared/src/main/resources/cargo-rates.yml}）で、
 * 出典が 1 つでなければ片方だけ直る——見積が旧料率、請求が新料率という状態は、
 * 荷主から見れば「言われた金額と違う」である。</p>
 *
 * <p><b>式も共有しない。</b> 見積の入力は候補経路、請求の入力は実際に通った
 * 区間なので、金額は一致しない（区間数の増減・誤配・留置）。同じ料率を読んで
 * いることは {@code RateTableParityTest} が固定する。</p>
 *
 * <p><b>欠けていれば組み立ての時点で断る。</b> 見積のたびに落ちるより、起動して
 * すぐ分かるほうがよい。</p>
 *
 * @param baseFare 基準運賃（円。1 区間・1,000kg・一般貨物）
 * @param regionFactors 地域係数
 * @param cargoTypeFactors 貨物種別係数
 * @param countryRegions 国コード → 地域区分。<b>表に無い国は遠洋</b>として数える
 */
public record QuotationRates(
        BigDecimal baseFare,
        Map<PortRegion, BigDecimal> regionFactors,
        Map<String, BigDecimal> cargoTypeFactors,
        Map<String, PortRegion> countryRegions) {

    public QuotationRates {
        if (baseFare == null || baseFare.signum() <= 0) {
            throw new BusinessRuleViolation(
                    "基準運賃が設定されていません（cargo.rates.base-fare）: " + baseFare);
        }
        if (regionFactors == null || regionFactors.size() < PortRegion.values().length) {
            throw new BusinessRuleViolation(
                    "地域係数が足りません（cargo.rates.region-factors）: " + regionFactors);
        }
        if (cargoTypeFactors == null || cargoTypeFactors.isEmpty()) {
            throw new BusinessRuleViolation("貨物種別係数が設定されていません");
        }
        regionFactors = Map.copyOf(regionFactors);
        cargoTypeFactors = Map.copyOf(cargoTypeFactors);
        countryRegions = countryRegions == null ? Map.of() : Map.copyOf(countryRegions);
    }

    /**
     * 区分の係数。
     *
     * <p><b>ここでは検査しない。</b> 全区分そろっていることは組み立ての時点で
     * 断ってある——同じ判定を 2 か所に書くと、片方だけが古くなる。</p>
     */
    public BigDecimal regionFactor(PortRegion region) {
        return regionFactors.get(region);
    }

    /**
     * 貨物種別の係数。
     *
     * <p><b>知らない種別は断る。</b> 黙って 1.0 で通すと、危険物が一般貨物の
     * 概算で見積もられ、請求との差が「説明できない差」になる。</p>
     */
    public BigDecimal cargoTypeFactor(CargoType cargoType) {
        BigDecimal factor = cargoTypeFactors.get(cargoType.name());
        if (factor == null) {
            throw new BusinessRuleViolation("料率表に無い貨物種別です: " + cargoType);
        }
        return factor;
    }

    /** 港の地域区分。<b>表に無い国は遠洋</b>（安いほうに倒さない）。 */
    public PortRegion regionOf(UnLocode port) {
        return countryRegions.getOrDefault(port.countryCode().value(), PortRegion.OCEAN);
    }

    /** 区間の地域区分は<b>両端の重いほう</b>（正典）。 */
    public PortRegion regionOfLeg(UnLocode from, UnLocode to) {
        return regionOf(from).heavier(regionOf(to));
    }
}
