package com.example.billingms.domain.model.valueobjects;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.util.Arrays;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

/**
 * 法人割引（US22・[ADR-027] 決定 1）。
 *
 * <p><strong>割引率は荷主に登録済みである</strong>（US03・IT2）。経理担当者が入力する
 * のではない——手で入れると、契約と違う率が入る。
 */
@DisplayName("法人割引")
class DiscountPolicyTest {

    @Nested
    @DisplayName("割引率")
    class Rate {

        /** 値域は 0〜30%（正典のビジネスルール 4）。 */
        @Test
        @DisplayName("0% から 30% までを受け付ける")
        void acceptsTheAgreedRange() {
            assertThat(DiscountRate.of(new BigDecimal("0.0000")).value())
                    .isEqualByComparingTo("0.0000");
            assertThat(DiscountRate.of(new BigDecimal("0.3000")).value())
                    .isEqualByComparingTo("0.3000");
        }

        /**
         * <strong>境界のすぐ外を断る。</strong>
         *
         * <p>30% を超える割引は契約に無い。通すと、入力の誤りがそのまま請求額になる。
         */
        @Test
        @DisplayName("30% を超える割引率は断る")
        void rejectsRatesBeyondTheContract() {
            BigDecimal aboveMax = new BigDecimal("0.3001");
            BigDecimal negative = new BigDecimal("-0.0001");

            assertThatThrownBy(() -> DiscountRate.of(aboveMax))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("30");
            assertThatThrownBy(() -> DiscountRate.of(negative))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("割引率が無い状態は作れない")
        void requiresAValue() {
            assertThatThrownBy(() -> DiscountRate.of(null))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        /** 画面に率を出すため、百分率でも読める（22-4）。 */
        @Test
        @DisplayName("百分率で読める")
        void readsAsPercentage() {
            assertThat(DiscountRate.of(new BigDecimal("0.1000")).percentage())
                    .isEqualByComparingTo("10");
        }
    }

    @Nested
    @DisplayName("方針の種別")
    class PolicyTypes {

        /**
         * <strong>本 IT で実装するのは 2 値だけ</strong>（[ADR-027] 注 10）。
         *
         * <p>`VOLUME_DISCOUNT` / `SEASONAL` は正典に定義があるが、US22 の受入基準に無く、
         * <strong>決める相手（契約条件）がいない</strong>。宣言だけしても、算定に使われない
         * まま `switch` が網羅していることになる——IT10 Problem 3 と同じ形。
         */
        @Test
        @DisplayName("割引方針は、いま 2 値である")
        void hasTheAgreedValues() {
            assertThat(Arrays.stream(DiscountPolicyType.values()).map(Enum::name))
                    .as("割引方針が増減した。**算定に使う場所も足すこと**"
                            + "——宣言だけだと、その方針は業務として空のままになる")
                    .containsExactly("CORPORATE_STANDARD", "NONE");
        }

        /**
         * <strong>すべての値が算定に使われる</strong>（Try 3 の一般形）。
         *
         * <p>見るのは<strong>割引額を答えられること</strong>である。率そのものは
         * {@code NONE} で {@code null}——「割引が無い」ことを 0% と区別するため
         * （[ADR-012]）。率の有無で判定すると、この検査は設計を否定してしまう。
         */
        @ParameterizedTest
        @EnumSource(DiscountPolicyType.class)
        @DisplayName("すべての割引方針が、割引額を答えられる")
        void everyPolicyResolvesADiscount(DiscountPolicyType type) {
            DiscountPolicy policy = DiscountPolicy.of(type, DiscountRate.of(new BigDecimal("0.1")));

            assertThat(policy.discountOf(Money.yen(new BigDecimal("100000"))))
                    .as("%s の扱いが決まっていない。割引額が出ない", type)
                    .isNotNull();
            assertThat(policy.applies())
                    .as("%s で、率の有無と適用の有無が食い違っている", type)
                    .isEqualTo(policy.rate() != null);
        }
    }

    @Nested
    @DisplayName("適用")
    class Application {

        /** 法人荷主には契約割引が適用される（22-1・22-2）。 */
        @Test
        @DisplayName("法人には契約割引が適用される")
        void appliesTheContractRateToCorporateShippers() {
            DiscountPolicy policy = DiscountPolicy.forCorporate(
                    DiscountRate.of(new BigDecimal("0.1000")));

            assertThat(policy.type()).isEqualTo(DiscountPolicyType.CORPORATE_STANDARD);
            assertThat(policy.discountOf(Money.yen(new BigDecimal("420000"))))
                    .isEqualTo(Money.yen(new BigDecimal("42000")));
        }

        /**
         * <strong>個人荷主には割引が無い</strong>（22-3）。
         *
         * <p><strong>0% ではなく「無い」。</strong>0% を出すと「割引が 0 だった」に読め、
         * 契約が無いことと区別できない（[ADR-012] が `DiscountRate` について同じ判断）。
         */
        @Test
        @DisplayName("個人には割引が無い")
        void appliesNoDiscountToIndividualShippers() {
            DiscountPolicy policy = DiscountPolicy.none();

            assertThat(policy.type()).isEqualTo(DiscountPolicyType.NONE);
            assertThat(policy.rate()).isNull();
            assertThat(policy.discountOf(Money.yen(new BigDecimal("420000"))))
                    .isEqualTo(Money.zero());
        }

        /**
         * <strong>法人でも割引率が未設定なら、割引は無い</strong>（[ADR-012]）。
         *
         * <p>未設定は 0% ではない。0% として扱うと、<strong>設定し忘れと
         * 「割引しない契約」が同じに見える</strong>。
         */
        @Test
        @DisplayName("法人でも割引率が未設定なら割引は無い")
        void appliesNoDiscountWhenTheRateIsUnset() {
            DiscountPolicy policy = DiscountPolicy.forCorporate(null);

            assertThat(policy.type()).isEqualTo(DiscountPolicyType.NONE);
            assertThat(policy.discountOf(Money.yen(new BigDecimal("420000"))))
                    .isEqualTo(Money.zero());
        }

        /** 割引額にも端数が出る。**丸めは `Money` が行う**（決定 2）。 */
        @Test
        @DisplayName("割引額の端数は 1 円に丸まる")
        void roundsTheDiscount() {
            DiscountPolicy policy = DiscountPolicy.forCorporate(
                    DiscountRate.of(new BigDecimal("0.1500")));

            assertThat(policy.discountOf(Money.yen(new BigDecimal("12345"))))
                    .isEqualTo(Money.yen(new BigDecimal("1852")));
        }
    }
}
