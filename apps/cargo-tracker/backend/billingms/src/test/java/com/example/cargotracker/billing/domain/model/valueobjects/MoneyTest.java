package com.example.cargotracker.billing.domain.model.valueobjects;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.cargotracker.shared.domain.error.BusinessRuleViolation;
import java.math.BigDecimal;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 金額（billingms の値オブジェクト）。
 *
 * <p><b>共有カーネルには置かない</b>（domain-model.md「置かないもの」）。BC ごとに
 * 通貨も丸めも違いうる。営業日（{@code HolidayCalendar}）を共有カーネルへ移すのとは
 * 逆向きの判断だが、理由が違う——営業日は全 BC で同じでなければならない。</p>
 */
class MoneyTest {

    private static Money yen(String amount) {
        return Money.yen(new BigDecimal(amount));
    }

    @Test
    @DisplayName("加算の順序を変えても合計が同じ（丸めが 1 か所だから）")
    void additionIsAssociative() {
        // **丸めが各所に散ると、足す順で合計が変わる。** 正典は「丸めは Money の
        // 中 1 か所だけ」と書いている。書いた保証を赤にできる形で固定する。
        Money a = yen("1000.004");
        Money b = yen("2000.004");
        Money c = yen("3000.004");

        assertThat(a.add(b).add(c).roundToUnit())
                .isEqualTo(c.add(b).add(a).roundToUnit());
        assertThat(a.add(b).add(c).roundToUnit().amount())
                .isEqualByComparingTo("6000");
    }

    @Test
    @DisplayName("1 円単位で四捨五入する（正典の丸め）")
    void roundsToTheYen() {
        assertThat(yen("100.5").roundToUnit().amount()).isEqualByComparingTo("101");
        assertThat(yen("100.4").roundToUnit().amount()).isEqualByComparingTo("100");
        // **切り捨てに変えると赤になる**（境界を両側から見る）。
        assertThat(yen("99.5").roundToUnit().amount()).isEqualByComparingTo("100");
    }

    @Test
    @DisplayName("通貨が違う金額は足せない（不変条件 1「通貨は集約内で一貫」）")
    void refusesMixedCurrencies() {
        Money dollars = new Money(new BigDecimal("100"), "USD");

        assertThatThrownBy(() -> yen("100").add(dollars))
                .isInstanceOf(BusinessRuleViolation.class)
                .hasMessageContaining("通貨");
        assertThatThrownBy(() -> yen("100").subtract(dollars))
                .isInstanceOf(BusinessRuleViolation.class);
    }

    @Test
    @DisplayName("掛ける・引く・比べるができる（料金の式が使う）")
    void supportsTheArithmeticTheFormulaNeeds() {
        assertThat(yen("1000").multiply(new BigDecimal("2.5")).amount())
                .isEqualByComparingTo("2500");
        assertThat(yen("1000").subtract(yen("150")).amount()).isEqualByComparingTo("850");
        assertThat(yen("1000").isGreaterThan(yen("999"))).isTrue();
        assertThat(yen("1000").isGreaterThan(yen("1000"))).isFalse();
    }

    @Test
    @DisplayName("負の金額は作れない（請求書に負の基本料金は無い）")
    void refusesNegativeAmounts() {
        assertThatThrownBy(() -> yen("-1"))
                .isInstanceOf(BusinessRuleViolation.class);
        // 引き算で負になるのも断る——割引が基本料金を超えたら、それは誤りである。
        assertThatThrownBy(() -> yen("100").subtract(yen("101")))
                .isInstanceOf(BusinessRuleViolation.class);
    }

    @Test
    @DisplayName("同じ金額・同じ通貨は等しい（値オブジェクト）")
    void equalsByValue() {
        assertThat(yen("1000")).isEqualTo(yen("1000"));
        assertThat(yen("1000")).isNotEqualTo(new Money(new BigDecimal("1000"), "USD"));
    }
}
