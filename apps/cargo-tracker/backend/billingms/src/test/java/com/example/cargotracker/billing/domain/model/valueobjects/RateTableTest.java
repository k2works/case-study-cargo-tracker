package com.example.cargotracker.billing.domain.model.valueobjects;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.cargotracker.shared.domain.error.BusinessRuleViolation;
import com.example.cargotracker.shared.domain.location.UnLocode;
import java.math.BigDecimal;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 料率表（正典の料金計算）。
 *
 * <p><b>料率をハードコードしない。</b> 見積（US01・IT14）も同じ値を読むので、
 * 出典が 1 つでなければ片方だけ直る。</p>
 */
class RateTableTest {

    private static RateTable table() {
        return RateTableFixture.canonical();
    }

    @Test
    @DisplayName("正典の値を持つ（基準運賃・地域係数・貨物種別係数・税率）")
    void carriesTheCanonicalRates() {
        RateTable rates = table();

        assertThat(rates.baseFare().amount()).isEqualByComparingTo("50000");
        assertThat(rates.regionFactor(PortRegion.DOMESTIC)).isEqualByComparingTo("1.0");
        assertThat(rates.regionFactor(PortRegion.NEAR_SEA)).isEqualByComparingTo("2.5");
        assertThat(rates.regionFactor(PortRegion.OCEAN)).isEqualByComparingTo("6.0");
        assertThat(rates.cargoTypeFactor("GENERAL")).isEqualByComparingTo("1.0");
        assertThat(rates.cargoTypeFactor("HAZARDOUS")).isEqualByComparingTo("1.8");
        assertThat(rates.cargoTypeFactor("REFRIGERATED")).isEqualByComparingTo("1.5");
        assertThat(rates.taxRate()).isEqualByComparingTo("0.10");
    }

    @Test
    @DisplayName("港の地域区分は国から決まる（国内・近海・遠洋）")
    void classifiesPortsByCountry() {
        RateTable rates = table();

        assertThat(rates.regionOf(new UnLocode("JPTYO"))).isEqualTo(PortRegion.DOMESTIC);
        assertThat(rates.regionOf(new UnLocode("SGSIN"))).isEqualTo(PortRegion.NEAR_SEA);
        assertThat(rates.regionOf(new UnLocode("USNYC"))).isEqualTo(PortRegion.OCEAN);
        // 表に無い国は遠洋として数える。**安いほうに倒さない**——取りこぼした
        // 請求はあとから取り返せない。
        assertThat(rates.regionOf(new UnLocode("ZZZZZ"))).isEqualTo(PortRegion.OCEAN);
    }

    @Test
    @DisplayName("区間の地域区分は両端の重いほう（正典）")
    void takesTheHeavierEndOfALeg() {
        RateTable rates = table();

        // JPTYO → SGSIN は 国内 と 近海 → 近海（2.5）
        assertThat(rates.regionOfLeg(new UnLocode("JPTYO"), new UnLocode("SGSIN")))
                .isEqualTo(PortRegion.NEAR_SEA);
        // SGSIN → USNYC は 近海 と 遠洋 → 遠洋（6.0）
        assertThat(rates.regionOfLeg(new UnLocode("SGSIN"), new UnLocode("USNYC")))
                .isEqualTo(PortRegion.OCEAN);
        // JPTYO → JPOSA は どちらも国内（1.0）
        assertThat(rates.regionOfLeg(new UnLocode("JPTYO"), new UnLocode("JPOSA")))
                .isEqualTo(PortRegion.DOMESTIC);
    }

    @Test
    @DisplayName("知らない貨物種別は断る（黙って 1.0 にしない）")
    void refusesUnknownCargoType() {
        // **黙って 1.0 で通すと、危険物が一般貨物の料金で請求される。**
        assertThatThrownBy(() -> table().cargoTypeFactor("UNKNOWN"))
                .isInstanceOf(BusinessRuleViolation.class)
                .hasMessageContaining("UNKNOWN");
    }

    @Test
    @DisplayName("設定が欠けていれば起動時に分かる（請求のたびに落ちない）")
    void refusesIncompleteConfiguration() {
        assertThatThrownBy(() -> new RateTable(null, Map.of(), Map.of(), Map.of(),
                new BigDecimal("0.10")))
                .isInstanceOf(BusinessRuleViolation.class);
        assertThatThrownBy(() -> new RateTable(Money.yen(new BigDecimal("50000")),
                Map.of(), Map.of(), Map.of(), new BigDecimal("0.10")))
                .isInstanceOf(BusinessRuleViolation.class);
    }

    @Test
    @DisplayName("設定の欠けは項目ごとに断る（どれが足りないか分かる）")
    void refusesEachMissingSetting() {
        Money fare = Money.yen(new BigDecimal("50000"));
        Map<PortRegion, BigDecimal> regions = table().regionFactors();
        Map<String, BigDecimal> cargoTypes = table().cargoTypeFactors();

        assertThatThrownBy(() -> new RateTable(fare, regions, Map.of(), Map.of(),
                new BigDecimal("0.10")))
                .as("貨物種別係数が空")
                .isInstanceOf(BusinessRuleViolation.class);
        assertThatThrownBy(() -> new RateTable(fare, regions, null, Map.of(),
                new BigDecimal("0.10")))
                .isInstanceOf(BusinessRuleViolation.class);
        assertThatThrownBy(() -> new RateTable(fare, null, cargoTypes, Map.of(),
                new BigDecimal("0.10")))
                .isInstanceOf(BusinessRuleViolation.class);
        assertThatThrownBy(() -> new RateTable(fare, regions, cargoTypes, Map.of(), null))
                .as("税率が無い")
                .isInstanceOf(BusinessRuleViolation.class);
        assertThatThrownBy(() -> new RateTable(fare, regions, cargoTypes, Map.of(),
                new BigDecimal("-0.1")))
                .as("税率が負")
                .isInstanceOf(BusinessRuleViolation.class);
    }

    @Test
    @DisplayName("国 → 地域区分を渡さなくても組める（すべて遠洋になる）")
    void worksWithoutCountryRegions() {
        RateTable rates = new RateTable(Money.yen(new BigDecimal("50000")),
                table().regionFactors(), table().cargoTypeFactors(), null,
                new BigDecimal("0.10"));

        assertThat(rates.regionOf(new UnLocode("JPTYO"))).isEqualTo(PortRegion.OCEAN);
    }

    @Test
    @DisplayName("地域係数が欠けていれば、引くときに断る")
    void refusesMissingRegionFactorAtLookup() {
        // 3 つ揃っていないと組めないので、組んだあとに引けない状況は起こりにくい。
        // それでも **黙って 0 を返さない**（区間係数が 0 だと料金も 0 になる）。
        Map<PortRegion, BigDecimal> incomplete = new java.util.EnumMap<>(PortRegion.class);
        incomplete.put(PortRegion.DOMESTIC, new BigDecimal("1.0"));
        incomplete.put(PortRegion.NEAR_SEA, new BigDecimal("2.5"));
        incomplete.put(PortRegion.OCEAN, null);

        assertThatThrownBy(() -> new RateTable(Money.yen(new BigDecimal("50000")), incomplete,
                table().cargoTypeFactors(), Map.of(), new BigDecimal("0.10")))
                .as("null を含む Map.copyOf は弾かれる")
                .isInstanceOf(NullPointerException.class);
    }
}
