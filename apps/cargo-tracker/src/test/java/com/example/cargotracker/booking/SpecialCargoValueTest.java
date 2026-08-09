package com.example.cargotracker.booking;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.cargotracker.booking.domain.model.HazardousDeclaration;
import com.example.cargotracker.booking.domain.model.TemperatureRequirement;
import com.example.cargotracker.booking.domain.model.TemperatureUnit;
import java.math.BigDecimal;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * 特別な取り扱いの値（US05 / IT12 の C6）。
 *
 * <p>IT9 のレビューが<strong>温度の境界（{@code min == max}）と危険物クラスの妥当性が
 * 未検証</strong>だと指摘し、3 イテレーション繰り越した。
 *
 * <p><strong>「必須である」だけを確かめると、何を書いても通る。</strong>
 * 危険物クラスは輸送書類にそのまま載る。存在しないクラスを書いた書類は、
 * <strong>申告が無いのと同じ結果（積み込み拒否・法令違反）</strong>になる。
 */
@DisplayName("特別な取り扱いの値（C6）")
class SpecialCargoValueTest {

    @Nested
    @DisplayName("温度管理条件")
    class 温度管理条件 {

        /**
         * <strong>上下が同じ指定は通す。</strong>
         *
         * <p>定温輸送（医薬品・精密機器）は実務にある。
         * <strong>「範囲」という語に引きずって上下を必ず離すと、
         * 実在する輸送が登録できなくなる。</strong>
         */
        @Test
        void 上下が同じ温度は定温輸送として通る() {
            TemperatureRequirement constant = new TemperatureRequirement(
                    new BigDecimal("2.0"), new BigDecimal("2.0"), TemperatureUnit.CELSIUS);

            assertThat(constant.display()).contains("2.0");
        }

        /** 入れ違いは打ち間違いとして日常的に起きる。**通すと条件を満たせない貨物を預かる。** */
        @Test
        void 上下が入れ違った指定は通らない() {
            assertThatThrownBy(() -> new TemperatureRequirement(
                    new BigDecimal("5.0"), new BigDecimal("-5.0"), TemperatureUnit.CELSIUS))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("上回っています");
        }

        /**
         * <strong>絶対零度より低い温度は物理的に存在しない。</strong>
         *
         * <p>{@code -999} は桁の打ち間違いとして起きる。上下の大小だけを見る検査は
         * これを通し、<strong>どの設備でも守れない条件の貨物を預かる</strong>ことになる。
         */
        @Test
        void 絶対零度を下回る温度は通らない() {
            assertThatThrownBy(() -> new TemperatureRequirement(
                    new BigDecimal("-999"), new BigDecimal("-18"), TemperatureUnit.CELSIUS))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("絶対零度");
        }

        /** 華氏でも同じ守りが働く。**単位ごとに絶対零度は違う。** */
        @Test
        void 華氏でも絶対零度を下回る温度は通らない() {
            assertThatThrownBy(() -> new TemperatureRequirement(
                    new BigDecimal("-500"), new BigDecimal("0"), TemperatureUnit.FAHRENHEIT))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("絶対零度");

            // 摂氏の下限（-273.15）は華氏では有効な温度である。
            // **単位を見ずに一律で弾く実装で緑にしない**
            assertThat(new TemperatureRequirement(
                    new BigDecimal("-300"), new BigDecimal("-100"), TemperatureUnit.FAHRENHEIT)
                    .display()).contains("-300");
        }
    }

    @Nested
    @DisplayName("危険物申告")
    class 危険物申告 {

        /** 国連分類は 1〜9 であり、区分（{@code 5.1} など）を持つものがある。 */
        @Test
        void 国連分類のクラスは通る() {
            for (String hazardClass : new String[] {"1", "2.3", "3", "4.1", "5.1", "6.1", "7", "8", "9"}) {
                assertThat(new HazardousDeclaration(hazardClass, "UN1263", "PAINT").hazardClass())
                        .isEqualTo(hazardClass);
            }
        }

        /**
         * <strong>存在しないクラスは通らない。</strong>
         *
         * <p>危険物クラスは輸送書類にそのまま載る。存在しないクラスを書いた書類は、
         * <strong>申告が無いのと同じ結果</strong>（積み込み拒否・法令違反）になる。
         */
        @Test
        void 国連分類にないクラスは通らない() {
            // **前後の空白は拒まない。** 入力の揺れであって、別のクラスを指すわけではない
            assertThat(new HazardousDeclaration(" 3 ", "UN1263", "PAINT").hazardClass())
                    .isEqualTo("3");
            for (String invalid : new String[] {"0", "10", "3.9", "1.7", "引火性液体", "III"}) {
                assertThatThrownBy(() -> new HazardousDeclaration(invalid, "UN1263", "PAINT"))
                        .as("クラス %s は国連分類に無い", invalid)
                        .isInstanceOf(IllegalArgumentException.class);
            }
        }

        /**
         * <strong>UN 番号は {@code UN} ＋ 4 桁である。</strong>
         *
         * <p>桁が欠けた番号は別の物質を指すか、どの物質も指さない。
         * <strong>書類の受理は税関で行われ、そこで止まると貨物は港に残る。</strong>
         */
        @Test
        void 番号の形式が違うものは通らない() {
            for (String invalid : new String[] {"1263", "UN126", "UN12634", "UNABCD"}) {
                assertThatThrownBy(() -> new HazardousDeclaration("3", invalid, "PAINT"))
                        .as("UN 番号 %s は形式が違う", invalid)
                        .isInstanceOf(IllegalArgumentException.class);
            }
            // **小文字を拒まない。** 入力の揺れであって、別の物質を指すわけではない
            assertThat(new HazardousDeclaration("3", "un1263", "PAINT").unNumber())
                    .isEqualTo("UN1263");
        }

        /**
         * <strong>形式の誤りは「申告が無い」に倒さない。</strong>
         *
         * <p>{@code ofNullable} が空を返すと、呼び出し側は「入力されていない」として
         * 扱う。<strong>誤った申告を黙って捨てると、危険物が一般貨物として運ばれる。</strong>
         */
        @Test
        void 形式の誤りは入力なしとして扱わない() {
            assertThatThrownBy(() -> HazardousDeclaration.ofNullable("99", "UN1263", "PAINT"))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }
}
