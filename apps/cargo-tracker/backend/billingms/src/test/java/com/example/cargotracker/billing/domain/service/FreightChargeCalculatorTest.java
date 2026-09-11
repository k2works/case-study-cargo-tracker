package com.example.cargotracker.billing.domain.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.cargotracker.billing.domain.model.valueobjects.FreightCharge;
import com.example.cargotracker.billing.domain.model.valueobjects.Money;
import com.example.cargotracker.billing.domain.model.valueobjects.PortRegion;
import com.example.cargotracker.billing.domain.model.valueobjects.RateTable;
import com.example.cargotracker.billing.domain.model.valueobjects.RateTableFixture;
import com.example.cargotracker.billing.domain.model.valueobjects.TransportRecord;
import com.example.cargotracker.shared.domain.error.BusinessRuleViolation;
import com.example.cargotracker.shared.domain.location.UnLocode;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 基本料金の計算（US21 §受入基準 3・正典の式）。
 *
 * <pre>
 * 基本料金 = 基準運賃 × 区間係数 × 重量係数 × 貨物種別係数
 * </pre>
 *
 * <p><b>係数を 1 つずつ動かす。</b> まとめて 1 件だけ確かめると、式のどの項を
 * 落としても「合計が違う」としか分からず、係数を掛け忘れた実装が別の係数の
 * 誤差で相殺されることもある。</p>
 */
class FreightChargeCalculatorTest {

    private final RateTable rates = RateTableFixture.canonical();
    private final FreightChargeCalculator calculator = new FreightChargeCalculator();

    private static UnLocode port(String value) {
        return new UnLocode(value);
    }

    private static TransportRecord transport(List<TransportRecord.BilledLeg> legs,
            String weightKg, String cargoType, String origin, String destination) {
        return new TransportRecord(legs, new BigDecimal(weightKg), cargoType,
                port(origin), port(destination));
    }

    /** 国内 1 区間・1,000kg・一般貨物 = 基準運賃そのもの。 */
    private static TransportRecord baseline() {
        return transport(List.of(new TransportRecord.BilledLeg(port("JPTYO"), port("JPOSA"))),
                "1000", "GENERAL", "JPTYO", "JPOSA");
    }

    private BigDecimal charge(TransportRecord transport) {
        return calculator.calculate(transport, rates).baseCharge().roundToUnit().amount();
    }

    @Test
    @DisplayName("基準：国内 1 区間・1,000kg・一般は基準運賃のまま")
    void baselineIsTheBaseFare() {
        assertThat(charge(baseline())).isEqualByComparingTo("50000");
    }

    @Test
    @DisplayName("区間係数だけを動かす（国内 1.0 → 近海 2.5 → 遠洋 6.0）")
    void movesTheRegionFactorAlone() {
        assertThat(charge(transport(
                List.of(new TransportRecord.BilledLeg(port("JPTYO"), port("SGSIN"))),
                "1000", "GENERAL", "JPTYO", "SGSIN")))
                .as("近海 2.5")
                .isEqualByComparingTo("125000");
        assertThat(charge(transport(
                List.of(new TransportRecord.BilledLeg(port("SGSIN"), port("USNYC"))),
                "1000", "GENERAL", "SGSIN", "USNYC")))
                .as("遠洋 6.0")
                .isEqualByComparingTo("300000");
    }

    @Test
    @DisplayName("区間係数は区間ごとの合計（2 区間なら足す）")
    void sumsTheRegionFactorsOfEveryLeg() {
        // JPTYO → SGSIN（近海 2.5）+ SGSIN → USNYC（遠洋 6.0）= 8.5
        assertThat(charge(transport(
                List.of(new TransportRecord.BilledLeg(port("JPTYO"), port("SGSIN")),
                        new TransportRecord.BilledLeg(port("SGSIN"), port("USNYC"))),
                "1000", "GENERAL", "JPTYO", "USNYC")))
                .as("区間を掛け算にすると 15.0 になって合わない")
                .isEqualByComparingTo("425000");
    }

    @Test
    @DisplayName("重量係数だけを動かす（重量 ÷ 1,000・下限 0.1）")
    void movesTheWeightFactorAlone() {
        assertThat(charge(transport(baseline().legs(), "1200", "GENERAL", "JPTYO", "JPOSA")))
                .isEqualByComparingTo("60000");
        // **下限 0.1。** 軽い貨物でも運ぶ手間は掛かる。
        assertThat(charge(transport(baseline().legs(), "10", "GENERAL", "JPTYO", "JPOSA")))
                .as("下限を外すと 500 円になる")
                .isEqualByComparingTo("5000");
        assertThat(charge(transport(baseline().legs(), "100", "GENERAL", "JPTYO", "JPOSA")))
                .as("ちょうど下限（0.1）")
                .isEqualByComparingTo("5000");
    }

    @Test
    @DisplayName("貨物種別係数だけを動かす（一般 1.0 / 危険物 1.8 / 冷凍 1.5）")
    void movesTheCargoTypeFactorAlone() {
        assertThat(charge(transport(baseline().legs(), "1000", "HAZARDOUS", "JPTYO", "JPOSA")))
                .isEqualByComparingTo("90000");
        assertThat(charge(transport(baseline().legs(), "1000", "REFRIGERATED", "JPTYO", "JPOSA")))
                .isEqualByComparingTo("75000");
    }

    @Test
    @DisplayName("根拠が読める形で返る（区間・地域区分・重量・貨物種別）")
    void explainsHowItWasCounted() {
        FreightCharge charge = calculator.calculate(transport(
                List.of(new TransportRecord.BilledLeg(port("JPTYO"), port("SGSIN")),
                        new TransportRecord.BilledLeg(port("SGSIN"), port("USNYC"))),
                "1200", "GENERAL", "JPTYO", "USNYC"), rates);

        assertThat(charge.regions()).containsExactly(PortRegion.NEAR_SEA, PortRegion.OCEAN);
        assertThat(charge.description())
                .as("金額だけでは「なぜこの額か」に答えられない")
                .contains("2 区間").contains("近海").contains("遠洋")
                .contains("1,200 kg").contains("一般");
        // **距離は出さない。** 数えていないものを根拠に書かない（注 N3）。
        assertThat(charge.description()).doesNotContain("km");
    }

    @Test
    @DisplayName("料率表に無い貨物種別は断る（黙って一般貨物の料金にしない）")
    void refusesUnknownCargoType() {
        assertThatThrownBy(() -> calculator.calculate(
                transport(baseline().legs(), "1000", "UNKNOWN", "JPTYO", "JPOSA"), rates))
                .isInstanceOf(BusinessRuleViolation.class);
    }

    @Test
    @DisplayName("丸めは Money の中だけ（途中で丸めない）")
    void roundsOnlyOnce() {
        // 1,234 kg・国内 1 区間 = 50,000 × 1.0 × 1.234 = 61,700。
        // 途中で重量係数を丸めると 50,000（1.0 倍）になる。
        assertThat(charge(transport(baseline().legs(), "1234", "GENERAL", "JPTYO", "JPOSA")))
                .isEqualByComparingTo("61700");
    }

    @Test
    @DisplayName("基本料金は Money として返る（通貨が一貫する）")
    void returnsMoney() {
        Money charge = calculator.calculate(baseline(), rates).baseCharge();
        assertThat(charge.currency()).isEqualTo(Money.JPY);
    }

    @Test
    @DisplayName("D5: 輸出（出発地と目的地の国が違う）は消費税 0 円")
    void exportIsTaxFree() {
        // **免税は「輸出かどうか」で決まる**（正典）。区間の地域区分ではない——
        // 近海の区間を通る国内輸送もあれば、国内区間だけの輸出もない。
        Money taxable = Money.yen(new BigDecimal("1000000"));

        assertThat(calculator.taxOn(transport(
                List.of(new TransportRecord.BilledLeg(port("JPTYO"), port("USNYC"))),
                "1000", "GENERAL", "JPTYO", "USNYC"), taxable, rates).isZero())
                .as("輸出免税")
                .isTrue();
        assertThat(calculator.taxOn(baseline(), taxable, rates).roundToUnit().amount())
                .as("国内は 10%")
                .isEqualByComparingTo("100000");
    }

    @Test
    @DisplayName("税は割引後の額に掛かる（割引前に掛けると取りすぎる）")
    void taxAppliesAfterTheDiscount() {
        Money afterDiscount = Money.yen(new BigDecimal("1011500"));

        assertThat(calculator.taxOn(baseline(), afterDiscount, rates).roundToUnit().amount())
                .isEqualByComparingTo("101150");
    }
}
