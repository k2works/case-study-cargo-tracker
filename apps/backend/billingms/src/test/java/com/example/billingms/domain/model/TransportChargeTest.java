package com.example.billingms.domain.model;

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
            TransportCharge charge = TransportCharge.of(2, new BigDecimal("4200"), CargoType.GENERAL);

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
            Money direct = TransportCharge.of(1, new BigDecimal("1000"), CargoType.GENERAL)
                    .baseAmount();
            Money viaOnePort = TransportCharge.of(2, new BigDecimal("1000"), CargoType.GENERAL)
                    .baseAmount();

            assertThat(viaOnePort.amount()).isEqualByComparingTo(direct.amount().multiply(
                    new BigDecimal("2")));
        }

        /** 貨物種別係数は正典の値をそのまま使う。 */
        @ParameterizedTest(name = "{0} の係数は {1}")
        @CsvSource({"GENERAL,1.0", "HAZARDOUS,1.8", "REFRIGERATED,1.5"})
        @DisplayName("貨物種別ごとの係数が効く")
        void appliesTheCargoTypeFactor(CargoType type, BigDecimal expected) {
            TransportCharge charge = TransportCharge.of(1, new BigDecimal("1000"), type);

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
            TransportCharge tiny = TransportCharge.of(1, new BigDecimal("1"), CargoType.GENERAL);

            assertThat(tiny.weightFactor()).isEqualByComparingTo("0.1");
            assertThat(tiny.baseAmount()).isEqualTo(Money.yen(new BigDecimal("5000")));
        }

        /**
         * <strong>1kg でも 100kg でも下限が効く境目を確かめる。</strong>
         *
         * <p>100kg = 係数 0.1 でちょうど下限。101kg からは実際の重量で計算される。
         */
        @Test
        @DisplayName("下限の境目で切り替わる")
        void switchesAtTheBoundary() {
            assertThat(TransportCharge.of(1, new BigDecimal("100"), CargoType.GENERAL)
                    .weightFactor()).isEqualByComparingTo("0.1");
            assertThat(TransportCharge.of(1, new BigDecimal("200"), CargoType.GENERAL)
                    .weightFactor()).isEqualByComparingTo("0.2");
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
            assertThat(TransportCharge.of(2, new BigDecimal("4200"), CargoType.GENERAL))
                    .as("DB から読み戻した重量が、書いた重量と違うものとして扱われる")
                    .isEqualTo(TransportCharge.of(2, new BigDecimal("4200.000"),
                            CargoType.GENERAL));
        }

        @Test
        @DisplayName("重量が違えば等しくない")
        void distinguishesDifferentWeights() {
            assertThat(TransportCharge.of(2, new BigDecimal("4200"), CargoType.GENERAL))
                    .isNotEqualTo(TransportCharge.of(2, new BigDecimal("4201"),
                            CargoType.GENERAL));
        }

        /** 同じものはハッシュも同じ。**集合や比較で壊れない。** */
        @Test
        @DisplayName("等しい根拠はハッシュも等しい")
        void hashesConsistently() {
            assertThat(TransportCharge.of(2, new BigDecimal("4200"), CargoType.GENERAL))
                    .hasSameHashCodeAs(TransportCharge.of(2, new BigDecimal("4200.000"),
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

            assertThatThrownBy(() -> TransportCharge.of(0, weight, CargoType.GENERAL))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("区間");
        }

        @Test
        @DisplayName("重量が 0 以下なら算定できない")
        void rejectsNonPositiveWeight() {
            BigDecimal negative = new BigDecimal("-1");

            assertThatThrownBy(() -> TransportCharge.of(1, BigDecimal.ZERO, CargoType.GENERAL))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("重量");
            assertThatThrownBy(() -> TransportCharge.of(1, negative, CargoType.GENERAL))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("貨物種別が無ければ算定できない")
        void rejectsMissingCargoType() {
            BigDecimal weight = new BigDecimal("1000");

            assertThatThrownBy(() -> TransportCharge.of(1, weight, null))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }
}
