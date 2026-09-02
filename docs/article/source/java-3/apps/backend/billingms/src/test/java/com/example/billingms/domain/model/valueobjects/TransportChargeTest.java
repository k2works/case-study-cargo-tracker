package com.example.billingms.domain.model.valueobjects;

import static com.example.billingms.ChargeFixtures.domesticLegs;
import static com.example.billingms.ChargeFixtures.oceanLegs;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/**
 * 基本料金の算定（[ADR-027] 決定 1）。
 *
 * <pre>
 * 基本料金 = 基準運賃 × 区間係数 × 重量係数 × 貨物種別係数
 * </pre>
 *
 * <p><strong>距離は使わない。</strong>港のマスタに緯度経度が無く、航海も距離を持たない。
 * 区間数で代替する——区間数が「どれだけ運んだか」に比例する唯一の実測値である。
 */
@DisplayName("輸送料金の算定")
class TransportChargeTest {

    @Nested
    @DisplayName("基本料金")
    class BaseCharge {

        /**
         * <strong>4 つの係数がすべて掛かる。</strong>
         *
         * <p>1 つでも掛け忘れると金額が変わる。50,000 × 2 区間 × 4.2 × 1.0 = 420,000。
         */
        @Test
        @DisplayName("基準運賃に区間・重量・貨物種別の係数を掛ける")
        void multipliesAllFactors() {
            TransportCharge charge = TransportCharge.of(domesticLegs(2), new BigDecimal("4200"), CargoType.GENERAL);

            assertThat(charge.baseAmount()).isEqualTo(Money.yen(new BigDecimal("420000")));
        }

        /**
         * <strong>区間数が距離の代わりである</strong>（決定 1）。
         *
         * <p>係数を掛け忘れると、直行も積み替え 2 回も同じ金額になる。
         */
        @Test
        @DisplayName("区間数が増えると料金も増える")
        void chargesMoreForMoreLegs() {
            Money direct = TransportCharge.of(domesticLegs(1), new BigDecimal("1000"), CargoType.GENERAL)
                    .baseAmount();
            Money viaOnePort = TransportCharge.of(domesticLegs(2), new BigDecimal("1000"), CargoType.GENERAL)
                    .baseAmount();

            assertThat(viaOnePort.amount()).isEqualByComparingTo(direct.amount().multiply(
                    new BigDecimal("2")));
        }

        /**
         * <strong>同じ区間数でも、遠洋なら高い</strong>（決定 1 の改訂）。
         *
         * <p>これが IT11 の未達（受入基準 21-2）である。区間数だけで測っていたため、
         * 東京 → 横浜と東京 → ロサンゼルスが同額になり、経理担当者は
         * 「これでは荷主が納得しない」と述べた。
         *
         * <p><strong>国内が変わらないことと対で見る</strong>——遠洋が高いことだけを
         * 見ると、一律に値上げした実装でも緑になる。
         */
        @Test
        @DisplayName("同じ 1 区間でも、国内と遠洋で金額が違う")
        void chargesMoreForOceanLegs() {
            Money domestic = TransportCharge.of(domesticLegs(1), new BigDecimal("1000"),
                    CargoType.GENERAL).baseAmount();
            Money ocean = TransportCharge.of(oceanLegs(1), new BigDecimal("1000"),
                    CargoType.GENERAL).baseAmount();

            assertThat(domestic)
                    .as("国内の運賃まで変わっている。地域区分の追加は値上げではない")
                    .isEqualTo(Money.yen(new BigDecimal("50000")));
            assertThat(ocean.amount())
                    .as("国内の積み替え 1 回と太平洋横断が同額になっている")
                    .isEqualByComparingTo(new BigDecimal("300000"));
        }

        /** 旅程で最も重い区分を根拠として持つ。**画面に出す**（決定 1）。 */
        @Test
        @DisplayName("旅程で最も重い地域区分を根拠として持つ")
        void keepsTheHeaviestRegionAsEvidence() {
            TransportCharge charge = TransportCharge.of(
                    java.util.List.of(
                            new ChargeableLeg(PortRegion.DOMESTIC, PortRegion.DOMESTIC),
                            new ChargeableLeg(PortRegion.DOMESTIC, PortRegion.OCEAN)),
                    new BigDecimal("1000"), CargoType.GENERAL);

            assertThat(charge.region()).isEqualTo(PortRegion.OCEAN);
            // 1.0 + 6.0 = 7.0
            assertThat(charge.legFactor()).isEqualByComparingTo("7.0");
        }

        /** 貨物種別係数は正典の値をそのまま使う。 */
        @ParameterizedTest(name = "{0} の係数は {1}")
        @CsvSource({"GENERAL,1.0", "HAZARDOUS,1.8", "REFRIGERATED,1.5"})
        @DisplayName("貨物種別ごとの係数が効く")
        void appliesTheCargoTypeFactor(CargoType type, BigDecimal expected) {
            TransportCharge charge = TransportCharge.of(domesticLegs(1), new BigDecimal("1000"), type);

            assertThat(charge.cargoTypeFactor()).isEqualByComparingTo(expected);
            assertThat(charge.baseAmount())
                    .isEqualTo(Money.yen(new BigDecimal("50000").multiply(expected)));
        }

        /**
         * <strong>重量係数に下限を置く</strong>（決定 1）。
         *
         * <p>置かないと、軽量の貨物が 0 円に近づく。<strong>運ぶ手間は重量に比例しない。</strong>
         */
        @Test
        @DisplayName("軽い貨物でも、重量係数は下限を下回らない")
        void appliesTheMinimumWeightFactor() {
            TransportCharge tiny = TransportCharge.of(domesticLegs(1), new BigDecimal("1"), CargoType.GENERAL);

            assertThat(tiny.weightFactor()).isEqualByComparingTo("0.1");
            assertThat(tiny.baseAmount()).isEqualTo(Money.yen(new BigDecimal("5000")));
        }

        /**
         * <strong>境目そのものを踏む</strong>（99 / 100 / 101）。
         *
         * <p>100kg = 係数 0.1 でちょうど下限。<strong>200kg で確かめても、
         * 比較の向きを誤っている実装（&lt; ではなく &lt;=、あるいは 100 ではなく
         * 1000 を境目にした実装）を判別しない</strong>——離れた値はどちらでも通る。
         *
         * <p>切り替わるのは 100 と 101 のあいだである。99 と 100 は同じ 0.1 に
         * なるが、<strong>下限が効いている側</strong>を 2 点で押さえておくと、
         * 下限を外したときに 2 つとも赤くなる。
         */
        @Test
        @DisplayName("下限の境目で切り替わる")
        void switchesAtTheBoundary() {
            assertThat(TransportCharge.of(domesticLegs(1), new BigDecimal("99"), CargoType.GENERAL)
                    .weightFactor()).isEqualByComparingTo("0.1");
            assertThat(TransportCharge.of(domesticLegs(1), new BigDecimal("100"), CargoType.GENERAL)
                    .weightFactor()).isEqualByComparingTo("0.1");
            assertThat(TransportCharge.of(domesticLegs(1), new BigDecimal("101"), CargoType.GENERAL)
                    .weightFactor()).isEqualByComparingTo("0.101");
        }
    }

    @Nested
    @DisplayName("等価性")
    class Equality {

        /**
         * <strong>同じ重量なら等しい</strong>（{@link Money} と同じ扱い）。
         *
         * <p>DB から読み戻した重量は列の桁数どおりの端数を持つ（{@code NUMERIC(10,3)} なら
         * {@code 4200.000}）。{@code BigDecimal} の {@code equals} は<strong>桁数まで
         * 見る</strong>ため、そのままだと 4200 と 4200.000 が「違う」と判定される
         * ——書いたとおりに戻ったかを確かめる検査が、書いたとおりに戻っているのに落ちる。
         */
        @Test
        @DisplayName("桁数が違っても、同じ重量なら等しい")
        void comparesTheWeightByValue() {
            assertThat(TransportCharge.of(domesticLegs(2), new BigDecimal("4200"), CargoType.GENERAL))
                    .as("DB から読み戻した重量が、書いた重量と違うものとして扱われる")
                    .isEqualTo(TransportCharge.of(domesticLegs(2), new BigDecimal("4200.000"),
                            CargoType.GENERAL));
        }

        @Test
        @DisplayName("重量が違えば等しくない")
        void distinguishesDifferentWeights() {
            assertThat(TransportCharge.of(domesticLegs(2), new BigDecimal("4200"), CargoType.GENERAL))
                    .isNotEqualTo(TransportCharge.of(domesticLegs(2), new BigDecimal("4201"),
                            CargoType.GENERAL));
        }

        /** 同じものはハッシュも同じ。**集合や比較で壊れない。** */
        @Test
        @DisplayName("等しい根拠はハッシュも等しい")
        void hashesConsistently() {
            assertThat(TransportCharge.of(domesticLegs(2), new BigDecimal("4200"), CargoType.GENERAL))
                    .hasSameHashCodeAs(TransportCharge.of(domesticLegs(2), new BigDecimal("4200.000"),
                            CargoType.GENERAL));
        }
    }

    @Nested
    @DisplayName("成り立たない入力")
    class InvalidInput {

        /**
         * <strong>区間が 0 本の旅程では料金を出さない。</strong>
         *
         * <p>0 を通すと料金が 0 円になり、<strong>運んだのに請求しない</strong>。
         * 旅程が無いのに引取済になっている貨物は、どこかが壊れている。
         */
        @Test
        @DisplayName("区間が 1 本も無ければ算定できない")
        void rejectsAnItineraryWithoutLegs() {
            BigDecimal weight = new BigDecimal("1000");
            // **旅程はラムダの外で組む。**中で組むと、例外を投げたのが旅程の組み立てか
            // 料金の算定かを判別できない（IT11 の `InvoiceTest` と同じ理由）
            var noLegs = domesticLegs(0);

            assertThatThrownBy(() -> TransportCharge.of(noLegs, weight, CargoType.GENERAL))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("区間");
        }

        @Test
        @DisplayName("重量が 0 以下なら算定できない")
        void rejectsNonPositiveWeight() {
            BigDecimal negative = new BigDecimal("-1");
            var legs = domesticLegs(1);

            assertThatThrownBy(() -> TransportCharge.of(legs, BigDecimal.ZERO,
                    CargoType.GENERAL))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("重量");
            assertThatThrownBy(() -> TransportCharge.of(legs, negative, CargoType.GENERAL))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("貨物種別が無ければ算定できない")
        void rejectsMissingCargoType() {
            BigDecimal weight = new BigDecimal("1000");
            var legs = domesticLegs(1);

            assertThatThrownBy(() -> TransportCharge.of(legs, weight, null))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }
}
