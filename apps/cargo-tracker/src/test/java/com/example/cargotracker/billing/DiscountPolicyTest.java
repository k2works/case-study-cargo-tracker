package com.example.cargotracker.billing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.cargotracker.billing.domain.model.valueobjects.DiscountPolicy;
import com.example.cargotracker.billing.domain.model.valueobjects.DiscountPolicyType;
import com.example.cargotracker.billing.domain.model.valueobjects.DiscountRate;
import com.example.cargotracker.billing.domain.model.valueobjects.Money;
import java.math.BigDecimal;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * 割引率と割引方針（US22）。
 *
 * <p><strong>法人荷主には荷主ごとの契約割引率が適用される。</strong>
 * 旧版の {@code DiscountPolicy.calculateRate(shipperType, amount)} は荷主種別と金額から
 * 割引率を算出する設計で、<strong>US03 / US22 が要求する「荷主ごとの契約割引率」を
 * 参照していなかった</strong>（設計レビュー H15）。契約率は
 * {@code ShipperDiscountPort} 経由で Shipper Context から取得する。
 *
 * <p><strong>個人荷主は「割引なし」ではなく率 0% として同じ道を通す。</strong>
 * 分岐で計算そのものを飛ばすと、請求書の形が 2 種類できる
 * （割引の行が無い形と、率 0% の行がある形）。
 */
@DisplayName("割引率と割引方針（US22）")
class DiscountPolicyTest {

    @Nested
    @DisplayName("割引率")
    class 割引率 {

        /**
         * <strong>上限 30% はドメインの不変条件である</strong>
         * （{@code domain-model.md} ビジネスルール 4）。
         *
         * <p><strong>画面に別の上限を書かない。</strong> 上限が 2 か所にあると、
         * どちらが正なのか分からなくなる。
         */
        @Test
        void 上限は三十パーセントである() {
            assertThat(DiscountRate.of(new BigDecimal("0.3000")).value())
                    .isEqualTo(new BigDecimal("0.3000"));
            assertThatThrownBy(() -> DiscountRate.of(new BigDecimal("0.3001")))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("30%");
        }

        /**
         * <strong>負の割引率は「割増」である。</strong> それは割引ではない。
         * 値上げを割引率で表すと、請求書に「割引 -10%」と印字される。
         */
        @Test
        void 負の割引率は作れない() {
            assertThatThrownBy(() -> DiscountRate.of(new BigDecimal("-0.01")))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        /** 割引なしは 0% として表す（<strong>null で表さない</strong>）。 */
        @Test
        void 割引なしはゼロパーセントである() {
            assertThat(DiscountRate.none().isNone()).isTrue();
            assertThat(DiscountRate.none().value().signum()).isZero();
        }

        /** 画面に出す百分率。**表示のための変換をドメインに置く。** */
        @Test
        void 百分率で読める() {
            assertThat(DiscountRate.of(new BigDecimal("0.1500")).asPercent())
                    .isEqualByComparingTo(new BigDecimal("15.00"));
        }
    }

    @Nested
    @DisplayName("割引方針")
    class 割引方針 {

        /**
         * <strong>法人荷主には契約割引率をそのまま適用する。</strong>
         *
         * <p>金額から割引率を決めない（レビュー H15 の是正）。
         */
        @Test
        void 法人荷主には契約割引率を適用する() {
            DiscountPolicy policy = DiscountPolicy.forCorporate();

            assertThat(policy.type()).isEqualTo(DiscountPolicyType.CORPORATE_CONTRACT);
            assertThat(policy.resolveRate(DiscountRate.of(new BigDecimal("0.1500"))).value())
                    .isEqualByComparingTo(new BigDecimal("0.1500"));
        }

        /**
         * <strong>個人荷主には割引が適用されない。</strong>
         *
         * <p>契約率が渡ってきても無視する。<strong>種別のほうが強い</strong> —
         * 個人荷主に契約率が付いていること自体が誤りであり、
         * それを黙って適用すると誤りが請求額に化ける。
         */
        @Test
        void 個人荷主には割引が適用されない() {
            DiscountPolicy policy = DiscountPolicy.forIndividual();

            assertThat(policy.type()).isEqualTo(DiscountPolicyType.NONE);
            assertThat(policy.resolveRate(DiscountRate.of(new BigDecimal("0.1500"))).isNone())
                    .as("契約率が渡ってきても個人には適用しない")
                    .isTrue();
        }

        /**
         * <strong>法人でも契約率が無ければ割引しない。</strong>
         *
         * <p>法人として登録されているが契約割引率が未設定という状態はありうる。
         * <strong>そこで例外にすると、請求そのものが止まる。</strong>
         * 割引なしで請求できることのほうが業務として正しい。
         */
        @Test
        void 法人でも契約率が無ければ割引しない() {
            assertThat(DiscountPolicy.forCorporate().resolveRate(null).isNone()).isTrue();
        }

        /**
         * <strong>個人荷主も同じ道を通る。</strong>
         *
         * <p>率 0% として計算すると、割引後の金額は基本料金と等しくなる。
         * <strong>計算を飛ばさない</strong>ので、請求書の形は 1 種類のままである。
         */
        @Test
        void 個人荷主でも割引後の金額が算出される() {
            Money base = Money.yen(new BigDecimal("100000"));
            DiscountRate rate = DiscountPolicy.forIndividual().resolveRate(null);

            assertThat(base.multiply(rate.discountFactor()).value())
                    .as("率 0% でも同じ計算を通る")
                    .isEqualTo(new BigDecimal("100000"));
        }

        /** 割引後の係数（{@code 1 - 割引率}）。**画面や計算側で引き算を書き直さない。** */
        @Test
        void 割引後の係数を返す() {
            assertThat(DiscountRate.of(new BigDecimal("0.1500")).discountFactor())
                    .isEqualByComparingTo(new BigDecimal("0.8500"));
        }
    }
}
