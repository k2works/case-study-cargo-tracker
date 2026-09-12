package com.example.cargotracker.booking.domain.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.cargotracker.booking.domain.model.valueobjects.CargoType;
import com.example.cargotracker.booking.domain.model.valueobjects.Leg;
import com.example.cargotracker.booking.domain.model.valueobjects.PortRegion;
import com.example.cargotracker.booking.domain.model.valueobjects.QuotationRates;
import com.example.cargotracker.booking.domain.model.valueobjects.RouteCandidate;
import com.example.cargotracker.booking.domain.model.valueobjects.Weight;
import com.example.cargotracker.shared.domain.error.BusinessRuleViolation;
import com.example.cargotracker.shared.domain.location.Location;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 概算料金の計算（US01 §受入基準 3・正典の料金計算）。
 *
 * <p><b>係数を 1 つずつ動かす。</b> まとめて確かめると、区間の「足す／掛ける」の
 * 取り違えも、重量係数の途中丸めも、同じ 1 本が赤くなるだけで区別できない。</p>
 *
 * <p>請求（{@code FreightChargeCalculator}）との同一性は
 * {@code RateTableParityTest} が固定する。ここは<b>式そのもの</b>を見る。</p>
 */
class QuotationEstimatorTest {

    private final QuotationEstimator estimator = new QuotationEstimator();

    /**
     * 料率。<b>実物の設定と同じ値</b>（`cargo-rates.yml`）。
     *
     * <p>値そのものが実物と一致していることは {@code RateTableParityTest} が
     * 実ファイルを読んで確かめる。ここではフィクスチャでよい——<b>式の検査</b>で
     * あって、設定の検査ではない。</p>
     */
    private static QuotationRates rates() {
        return new QuotationRates(new BigDecimal("50000"),
                Map.of(PortRegion.DOMESTIC, new BigDecimal("1.0"),
                        PortRegion.NEAR_SEA, new BigDecimal("2.5"),
                        PortRegion.OCEAN, new BigDecimal("6.0")),
                Map.of("GENERAL", new BigDecimal("1.0"),
                        "HAZARDOUS", new BigDecimal("1.8"),
                        "REFRIGERATED", new BigDecimal("1.5")),
                Map.of("JP", PortRegion.DOMESTIC, "SG", PortRegion.NEAR_SEA));
    }

    private static Leg leg(String from, String to) {
        return new Leg("V-Q-001", Location.of(from), Location.of(to),
                Instant.parse("2026-10-01T09:00:00Z"), Instant.parse("2026-10-15T18:00:00Z"));
    }

    @Test
    @DisplayName("1 区間・国内・1,000kg・一般は基準運賃そのもの")
    void baselineIsTheBaseFare() {
        assertThat(estimator.estimateOne(List.of(leg("JPTYO", "JPOSA")),
                CargoType.GENERAL, Weight.ofKilograms("1000"), rates()).amount())
                .isEqualByComparingTo("50000");
    }

    @Test
    @DisplayName("区間は足す（掛けない）。2 区間なら係数の合計になる")
    void addsRegionFactorsPerLeg() {
        // 近海 2.5 + 遠洋 6.0 = 8.5 → 50,000 × 8.5 = 425,000
        assertThat(estimator.estimateOne(
                List.of(leg("JPTYO", "SGSIN"), leg("SGSIN", "USNYC")),
                CargoType.GENERAL, Weight.ofKilograms("1000"), rates()).amount())
                .as("掛けると 50,000 × 2.5 × 6.0 = 750,000 になり、区間を増やすほど跳ね上がる")
                .isEqualByComparingTo("425000");
    }

    @Test
    @DisplayName("区間の地域区分は両端の重いほう（国内 → 遠洋は遠洋）")
    void usesTheHeavierRegionOfBothEnds() {
        // 表に無い国（US）は遠洋。JP → US は遠洋 6.0。
        assertThat(estimator.estimateOne(List.of(leg("JPTYO", "USNYC")),
                CargoType.GENERAL, Weight.ofKilograms("1000"), rates()).amount())
                .isEqualByComparingTo("300000");
    }

    @Test
    @DisplayName("重量係数は 1,000kg 単位。途中で丸めない")
    void scalesByWeightWithoutRounding() {
        // 1,234 / 1,000 = 1.234 → 50,000 × 1.234 = 61,700
        assertThat(estimator.estimateOne(List.of(leg("JPTYO", "JPOSA")),
                CargoType.GENERAL, Weight.ofKilograms("1234"), rates()).amount())
                .as("丸めると 1,234 kg が 1,000 kg と同じ料金になる")
                .isEqualByComparingTo("61700");
    }

    @Test
    @DisplayName("重量係数には下限がある（軽い貨物でも運ぶ手間は掛かる）")
    void appliesTheMinimumWeightFactor() {
        // 50 / 1,000 = 0.05 だが下限は 0.1 → 50,000 × 0.1 = 5,000
        assertThat(estimator.estimateOne(List.of(leg("JPTYO", "JPOSA")),
                CargoType.GENERAL, Weight.ofKilograms("50"), rates()).amount())
                .isEqualByComparingTo("5000");
    }

    @Test
    @DisplayName("貨物種別の係数が掛かる（危険物 1.8・冷凍 1.5）")
    void appliesTheCargoTypeFactor() {
        assertThat(estimator.estimateOne(List.of(leg("JPTYO", "JPOSA")),
                CargoType.HAZARDOUS, Weight.ofKilograms("1000"), rates()).amount())
                .isEqualByComparingTo("90000");
        assertThat(estimator.estimateOne(List.of(leg("JPTYO", "JPOSA")),
                CargoType.REFRIGERATED, Weight.ofKilograms("1000"), rates()).amount())
                .isEqualByComparingTo("75000");
    }

    @Test
    @DisplayName("料率表に無い貨物種別は断る（黙って 1.0 で通さない）")
    void refusesUnknownCargoType() {
        QuotationRates withoutHazardous = new QuotationRates(new BigDecimal("50000"),
                Map.of(PortRegion.DOMESTIC, new BigDecimal("1.0"),
                        PortRegion.NEAR_SEA, new BigDecimal("2.5"),
                        PortRegion.OCEAN, new BigDecimal("6.0")),
                Map.of("GENERAL", new BigDecimal("1.0")),
                Map.of("JP", PortRegion.DOMESTIC));

        assertThatThrownBy(() -> estimator.estimateOne(List.of(leg("JPTYO", "JPOSA")),
                CargoType.HAZARDOUS, Weight.ofKilograms("1000"), withoutHazardous))
                .as("黙って 1.0 で通すと、危険物が一般貨物の概算で見積もられる")
                .isInstanceOf(BusinessRuleViolation.class);
    }

    @Test
    @DisplayName("消費税は載せない（輸出免税の判定は実際の輸送で決まる）")
    void doesNotIncludeTax() {
        // 国内（JP → JP）でも税は乗らない。乗ると請求との差が税の分だけ増える。
        assertThat(estimator.estimateOne(List.of(leg("JPTYO", "JPOSA")),
                CargoType.GENERAL, Weight.ofKilograms("1000"), rates()).amount())
                .isEqualByComparingTo("50000");
    }

    @Test
    @DisplayName("候補ごとに概算が付き、超過日数と所要日数はそのまま写る")
    void estimatesEachCandidate() {
        var candidates = List.of(
                new RouteCandidate(List.of(leg("JPTYO", "JPOSA")), 3, true, 0),
                new RouteCandidate(List.of(leg("JPTYO", "USNYC")), 20, true, 5));

        var quoted = estimator.estimate(candidates, CargoType.GENERAL,
                Weight.ofKilograms("1000"), rates());

        assertThat(quoted).hasSize(2);
        assertThat(quoted.get(0).estimatedCharge().amount()).isEqualByComparingTo("50000");
        assertThat(quoted.get(0).transitDays()).isEqualTo(3);
        assertThat(quoted.get(0).meetsDeadline()).isTrue();
        assertThat(quoted.get(1).estimatedCharge().amount()).isEqualByComparingTo("300000");
        assertThat(quoted.get(1).overdueDays())
                .as("超過日数は routingms が数えたものを写す（画面で数え直させない）")
                .isEqualTo(5);
        assertThat(quoted.get(1).meetsDeadline()).isFalse();
    }

    @Test
    @DisplayName("候補 0 件なら概算も 0 件（断らない）")
    void estimatesAnEmptyList() {
        assertThat(estimator.estimate(List.of(), CargoType.GENERAL,
                Weight.ofKilograms("1000"), rates())).isEmpty();
    }
}
