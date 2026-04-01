package com.example.cargotracker.quote.domain;

import com.example.cargotracker.quote.domain.model.valueobjects.CargoType;
import com.example.cargotracker.quote.domain.model.valueobjects.QuoteCondition;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.*;

@DisplayName("QuoteCondition 値オブジェクト")
class QuoteConditionTest {

    private static final LocalDate FUTURE_DATE = LocalDate.of(2025, 9, 1);

    @Test
    @DisplayName("正常な値で生成できる")
    void createValidCondition() {
        QuoteCondition condition = new QuoteCondition(
                "JPTYO", "USNYC", FUTURE_DATE, CargoType.GENERAL_CARGO, new BigDecimal("100.0")
        );

        assertThat(condition.originLocode()).isEqualTo("JPTYO");
        assertThat(condition.destinationLocode()).isEqualTo("USNYC");
        assertThat(condition.requestedArrivalDate()).isEqualTo(FUTURE_DATE);
        assertThat(condition.cargoType()).isEqualTo(CargoType.GENERAL_CARGO);
        assertThat(condition.weightKg()).isEqualByComparingTo("100.0");
    }

    @Test
    @DisplayName("originLocode が null の場合は例外を投げる")
    void rejectNullOriginLocode() {
        assertThatThrownBy(() -> new QuoteCondition(
                null, "USNYC", FUTURE_DATE, CargoType.GENERAL_CARGO, new BigDecimal("100.0")
        )).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("出発地");
    }

    @Test
    @DisplayName("originLocode が空文字の場合は例外を投げる")
    void rejectBlankOriginLocode() {
        assertThatThrownBy(() -> new QuoteCondition(
                "  ", "USNYC", FUTURE_DATE, CargoType.GENERAL_CARGO, new BigDecimal("100.0")
        )).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("出発地");
    }

    @Test
    @DisplayName("destinationLocode が null の場合は例外を投げる")
    void rejectNullDestinationLocode() {
        assertThatThrownBy(() -> new QuoteCondition(
                "JPTYO", null, FUTURE_DATE, CargoType.GENERAL_CARGO, new BigDecimal("100.0")
        )).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("目的地");
    }

    @Test
    @DisplayName("destinationLocode が空文字の場合は例外を投げる")
    void rejectBlankDestinationLocode() {
        assertThatThrownBy(() -> new QuoteCondition(
                "JPTYO", "", FUTURE_DATE, CargoType.GENERAL_CARGO, new BigDecimal("100.0")
        )).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("目的地");
    }

    @Test
    @DisplayName("requestedArrivalDate が null の場合は例外を投げる")
    void rejectNullRequestedArrivalDate() {
        assertThatThrownBy(() -> new QuoteCondition(
                "JPTYO", "USNYC", null, CargoType.GENERAL_CARGO, new BigDecimal("100.0")
        )).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("希望着日");
    }

    @Test
    @DisplayName("cargoType が null の場合は例外を投げる")
    void rejectNullCargoType() {
        assertThatThrownBy(() -> new QuoteCondition(
                "JPTYO", "USNYC", FUTURE_DATE, null, new BigDecimal("100.0")
        )).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("貨物種別");
    }

    @Test
    @DisplayName("weightKg が null の場合は例外を投げる")
    void rejectNullWeightKg() {
        assertThatThrownBy(() -> new QuoteCondition(
                "JPTYO", "USNYC", FUTURE_DATE, CargoType.GENERAL_CARGO, null
        )).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("重量");
    }

    @Test
    @DisplayName("weightKg が 0 の場合は例外を投げる")
    void rejectZeroWeightKg() {
        assertThatThrownBy(() -> new QuoteCondition(
                "JPTYO", "USNYC", FUTURE_DATE, CargoType.GENERAL_CARGO, BigDecimal.ZERO
        )).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("重量");
    }

    @Test
    @DisplayName("weightKg が負の場合は例外を投げる")
    void rejectNegativeWeightKg() {
        assertThatThrownBy(() -> new QuoteCondition(
                "JPTYO", "USNYC", FUTURE_DATE, CargoType.GENERAL_CARGO, new BigDecimal("-10.0")
        )).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("重量");
    }

    @Test
    @DisplayName("equals と hashCode が正しく動作する")
    void equalsAndHashCode() {
        QuoteCondition a = new QuoteCondition(
                "JPTYO", "USNYC", FUTURE_DATE, CargoType.GENERAL_CARGO, new BigDecimal("100.0")
        );
        QuoteCondition b = new QuoteCondition(
                "JPTYO", "USNYC", FUTURE_DATE, CargoType.GENERAL_CARGO, new BigDecimal("100.0")
        );

        assertThat(a).isEqualTo(b);
        assertThat(a.hashCode()).isEqualTo(b.hashCode());
    }
}
