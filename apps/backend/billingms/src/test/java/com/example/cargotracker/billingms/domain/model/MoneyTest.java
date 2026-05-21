package com.example.cargotracker.billingms.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.cargotracker.billingms.domain.model.valueobjects.Money;
import java.math.BigDecimal;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class MoneyTest {

    @Test
    @DisplayName("Money.of で指定通貨の Money が生成できる")
    void of_正常生成() {
        var money = Money.of(new BigDecimal("1000"), "JPY");
        assertThat(money.amount()).isEqualByComparingTo("1000");
        assertThat(money.currency().getCurrencyCode()).isEqualTo("JPY");
    }

    @Test
    @DisplayName("Money.ofJpy で JPY の Money が生成できる")
    void ofJpy_正常生成() {
        var money = Money.ofJpy(5000L);
        assertThat(money.amount()).isEqualByComparingTo("5000");
        assertThat(money.currency()).isEqualTo(Money.JPY);
    }

    @Test
    @DisplayName("amount が null の場合は NullPointerException")
    void constructor_amount_null() {
        assertThatThrownBy(() -> new Money(null, Money.JPY))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    @DisplayName("currency が null の場合は NullPointerException")
    void constructor_currency_null() {
        assertThatThrownBy(() -> new Money(BigDecimal.ZERO, null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    @DisplayName("amount が負の場合は IllegalArgumentException")
    void constructor_negative_amount() {
        var negativeAmount = new BigDecimal("-1");
        assertThatThrownBy(() -> new Money(negativeAmount, Money.JPY))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("0 以上");
    }

    @Test
    @DisplayName("multiply で金額を乗算できる（半端切り上げ）")
    void multiply_正常乗算() {
        var money = Money.ofJpy(100000L);
        var result = money.multiply(new BigDecimal("0.1"));
        assertThat(result.amount()).isEqualByComparingTo("10000");
    }

    @Test
    @DisplayName("subtract で差し引きができる")
    void subtract_正常差し引き() {
        var base = Money.ofJpy(100000L);
        var discount = Money.ofJpy(10000L);
        var result = base.subtract(discount);
        assertThat(result.amount()).isEqualByComparingTo("90000");
    }

    @Test
    @DisplayName("通貨が異なる場合は subtract で IllegalArgumentException")
    void subtract_通貨不一致() {
        var jpy = Money.ofJpy(100000L);
        var usd = Money.of(new BigDecimal("100"), "USD");
        assertThatThrownBy(() -> jpy.subtract(usd))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("通貨が一致しません");
    }

    @Test
    @DisplayName("差し引き後が負になる場合は IllegalArgumentException")
    void subtract_負になる() {
        var base = Money.ofJpy(100L);
        var larger = Money.ofJpy(200L);
        assertThatThrownBy(() -> base.subtract(larger))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("負になります");
    }

    @Test
    @DisplayName("toString で金額と通貨コードが表示される")
    void toString_正常() {
        var money = Money.ofJpy(1000L);
        assertThat(money).hasToString("1000 JPY");
    }
}
