package com.example.billingms.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * 金額（[ADR-027] 決定 2）。
 *
 * <p><strong>丸めはここ 1 か所だけで行う。</strong>呼び出し側が丸めると、丸める場所が
 * 増えるたびに結果が変わりうる——画面と保存値が食い違い、明細の合計と総額が 1 円ずれる。
 */
@DisplayName("金額")
class MoneyTest {

    @Nested
    @DisplayName("丸め")
    class Rounding {

        /**
         * <strong>1 円単位で四捨五入する</strong>（決定 2）。
         *
         * <p>割引後の金額には端数が出る（基本料金 × 0.85 など）。
         */
        @Test
        @DisplayName("端数は 1 円単位で四捨五入する")
        void roundsToTheNearestYen() {
            assertThat(Money.yen(new BigDecimal("100.4")).amount())
                    .isEqualByComparingTo("100");
            assertThat(Money.yen(new BigDecimal("100.5")).amount())
                    .isEqualByComparingTo("101");
            assertThat(Money.yen(new BigDecimal("100.6")).amount())
                    .isEqualByComparingTo("101");
        }

        /**
         * <strong>負の金額も同じ向きで丸める。</strong>
         *
         * <p>減額（明細）は負の数で入る。HALF_UP は 0 から遠いほうへ丸めるため、
         * -100.5 は -101 になる。<strong>「金額の絶対値で見て同じ扱い」</strong>であり、
         * 減額だけ有利／不利に倒れない。
         */
        @Test
        @DisplayName("負の金額も絶対値で見て同じ向きに丸める")
        void roundsNegativeAmountsSymmetrically() {
            assertThat(Money.yen(new BigDecimal("-100.5")).amount())
                    .isEqualByComparingTo("-101");
            assertThat(Money.yen(new BigDecimal("-100.4")).amount())
                    .isEqualByComparingTo("-100");
        }

        /** <strong>丸めた結果を保持する。</strong>あとから丸め直せる形で持たない。 */
        @Test
        @DisplayName("保持している値は丸めたあとの値である")
        void keepsTheRoundedValue() {
            Money money = Money.yen(new BigDecimal("420000.7"));

            assertThat(money.amount().scale())
                    .as("小数を抱えたままだと、表示のたびに丸め直すことになる")
                    .isLessThanOrEqualTo(0);
        }
    }

    @Nested
    @DisplayName("演算")
    class Arithmetic {

        /** 掛け算の結果も丸める。**掛けてから丸めるのであって、丸めてから掛けるのではない。** */
        @Test
        @DisplayName("掛け算の結果は丸めて返す")
        void multipliesAndRounds() {
            Money base = Money.yen(new BigDecimal("420000"));

            assertThat(base.multiply(new BigDecimal("0.085")).amount())
                    .isEqualByComparingTo("35700");
        }

        @Test
        @DisplayName("足し算と引き算ができる")
        void addsAndSubtracts() {
            Money base = Money.yen(new BigDecimal("420000"));

            assertThat(base.add(Money.yen(new BigDecimal("10000"))).amount())
                    .isEqualByComparingTo("430000");
            assertThat(base.subtract(Money.yen(new BigDecimal("42000"))).amount())
                    .isEqualByComparingTo("378000");
        }

        /**
         * <strong>通貨が違う金額は足せない。</strong>
         *
         * <p>足せてしまうと、円とドルが混ざった請求書ができる。多通貨は将来の話だが、
         * <strong>混ぜられる形にしておくと、混ざったことに気づけない</strong>。
         */
        @Test
        @DisplayName("通貨の違う金額は足せない")
        void rejectsMixedCurrencies() {
            Money yen = Money.yen(new BigDecimal("1000"));
            Money dollar = Money.of(new BigDecimal("1000"), "USD");

            assertThatThrownBy(() -> yen.add(dollar))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("通貨");
        }
    }

    @Nested
    @DisplayName("生成")
    class Creation {

        @Test
        @DisplayName("通貨コードは 3 文字である")
        void requiresAThreeLetterCurrencyCode() {
            assertThatThrownBy(() -> Money.of(BigDecimal.ONE, "JPYEN"))
                    .isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> Money.of(BigDecimal.ONE, null))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("金額が無い状態は作れない")
        void requiresAnAmount() {
            assertThatThrownBy(() -> Money.of(null, "JPY"))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        /** **0 円は作れる。** 調整で相殺されて 0 になることはある。 */
        @Test
        @DisplayName("0 円は作れる")
        void allowsZero() {
            assertThat(Money.yen(BigDecimal.ZERO).amount()).isEqualByComparingTo("0");
        }
    }

    @Nested
    @DisplayName("符号と表示")
    class SignAndText {

        /** 減額（明細）は負の数で入る。**符号で判別できないと、減額が加算に化ける。** */
        @Test
        @DisplayName("負の金額を判別できる")
        void tellsNegativeAmounts() {
            assertThat(Money.yen(new BigDecimal("-1")).isNegative()).isTrue();
            assertThat(Money.yen(BigDecimal.ZERO).isNegative()).isFalse();
            assertThat(Money.yen(new BigDecimal("1")).isNegative()).isFalse();
        }

        @Test
        @DisplayName("0 円を作れる")
        void createsZero() {
            assertThat(Money.zero()).isEqualTo(Money.yen(BigDecimal.ZERO));
            assertThat(Money.zero().currency()).isEqualTo("JPY");
        }

        /** ログや例外の文面に出る。**金額だけだと、通貨が分からない。** */
        @Test
        @DisplayName("金額と通貨を並べて表す")
        void showsAmountAndCurrency() {
            assertThat(Money.yen(new BigDecimal("1000"))).hasToString("1000 JPY");
        }

        /** **同じ金額はハッシュも同じ。** 集合やマップの鍵にしたときに壊れない。 */
        @Test
        @DisplayName("等しい金額はハッシュも等しい")
        void hashesConsistently() {
            assertThat(Money.yen(new BigDecimal("1000")).hashCode())
                    .isEqualTo(Money.yen(new BigDecimal("1000.0")).hashCode());
        }

        @Test
        @DisplayName("金額でないものとは等しくない")
        void isNotEqualToOtherTypes() {
            assertThat(Money.yen(BigDecimal.ONE)).isNotEqualTo("1 JPY").isNotEqualTo(null);
            assertThat(Money.yen(BigDecimal.ONE)).isEqualTo(Money.yen(BigDecimal.ONE));
        }
    }

    @Nested
    @DisplayName("引き算と掛け算の拒み方")
    class InvalidArithmetic {

        @Test
        @DisplayName("通貨の違う金額は引けない")
        void rejectsMixedCurrenciesOnSubtract() {
            Money yen = Money.yen(new BigDecimal("1000"));
            Money dollar = Money.of(new BigDecimal("1000"), "USD");

            assertThatThrownBy(() -> yen.subtract(dollar))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("通貨");
        }

        @Test
        @DisplayName("相手が無ければ計算できない")
        void rejectsMissingOperand() {
            Money yen = Money.yen(new BigDecimal("1000"));

            assertThatThrownBy(() -> yen.add(null))
                    .isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> yen.multiply(null))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Nested
    @DisplayName("等価性")
    class Equality {

        /** **同じ金額は同じ値である。** 値オブジェクトとして比較できないと、検査が書けない。 */
        @Test
        @DisplayName("同じ金額と通貨なら等しい")
        void comparesByValue() {
            assertThat(Money.yen(new BigDecimal("1000")))
                    .isEqualTo(Money.yen(new BigDecimal("1000")));
            assertThat(Money.yen(new BigDecimal("1000")))
                    .isNotEqualTo(Money.of(new BigDecimal("1000"), "USD"));
        }

        /**
         * <strong>丸める前が違っても、丸めた結果が同じなら等しい。</strong>
         *
         * <p>100.4 円と 100.4999 円はどちらも 100 円である。等価性が丸め前に依存すると、
         * 同じ請求金額なのに「違う」と判定される。
         */
        @Test
        @DisplayName("丸めた結果が同じなら等しい")
        void comparesAfterRounding() {
            assertThat(Money.yen(new BigDecimal("100.4")))
                    .isEqualTo(Money.yen(new BigDecimal("100.49")));
        }
    }
}
