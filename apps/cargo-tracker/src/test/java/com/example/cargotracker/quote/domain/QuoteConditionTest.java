package com.example.cargotracker.quote.domain;

import com.example.cargotracker.quote.domain.model.valueobjects.CargoType;
import com.example.cargotracker.quote.domain.model.valueobjects.QuoteCondition;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("QuoteCondition 値オブジェクト")
class QuoteConditionTest {

    private static final LocalDate FUTURE_DATE = LocalDate.of(2025, 9, 1);
    private static final BigDecimal DEFAULT_WEIGHT = new BigDecimal("100.0");

    @Test
    @DisplayName("正常な値で生成できる")
    void createValidCondition() {
        QuoteCondition condition = createCondition(
                "JPTYO",
                "USNYC",
                FUTURE_DATE,
                CargoType.GENERAL_CARGO,
                DEFAULT_WEIGHT
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
        assertThatThrownBy(() -> createCondition(
                null,
                "USNYC",
                FUTURE_DATE,
                CargoType.GENERAL_CARGO,
                DEFAULT_WEIGHT
        )).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("出発地");
    }

    @Test
    @DisplayName("originLocode が空文字の場合は例外を投げる")
    void rejectBlankOriginLocode() {
        assertThatThrownBy(() -> createCondition(
                "  ",
                "USNYC",
                FUTURE_DATE,
                CargoType.GENERAL_CARGO,
                DEFAULT_WEIGHT
        )).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("出発地");
    }

    @Test
    @DisplayName("destinationLocode が null の場合は例外を投げる")
    void rejectNullDestinationLocode() {
        assertThatThrownBy(() -> createCondition(
                "JPTYO",
                null,
                FUTURE_DATE,
                CargoType.GENERAL_CARGO,
                DEFAULT_WEIGHT
        )).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("目的地");
    }

    @Test
    @DisplayName("destinationLocode が空文字の場合は例外を投げる")
    void rejectBlankDestinationLocode() {
        assertThatThrownBy(() -> createCondition(
                "JPTYO",
                "",
                FUTURE_DATE,
                CargoType.GENERAL_CARGO,
                DEFAULT_WEIGHT
        )).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("目的地");
    }

    @Test
    @DisplayName("requestedArrivalDate が null の場合は例外を投げる")
    void rejectNullRequestedArrivalDate() {
        assertThatThrownBy(() -> createCondition(
                "JPTYO",
                "USNYC",
                null,
                CargoType.GENERAL_CARGO,
                DEFAULT_WEIGHT
        )).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("希望着日");
    }

    @Test
    @DisplayName("cargoType が null の場合は例外を投げる")
    void rejectNullCargoType() {
        assertThatThrownBy(() -> createCondition(
                "JPTYO",
                "USNYC",
                FUTURE_DATE,
                null,
                DEFAULT_WEIGHT
        )).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("貨物種別");
    }

    @Test
    @DisplayName("weightKg が null の場合は例外を投げる")
    void rejectNullWeightKg() {
        assertThatThrownBy(() -> createCondition(
                "JPTYO",
                "USNYC",
                FUTURE_DATE,
                CargoType.GENERAL_CARGO,
                null
        )).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("重量");
    }

    @Test
    @DisplayName("weightKg が 0 の場合は例外を投げる")
    void rejectZeroWeightKg() {
        assertThatThrownBy(() -> createCondition(
                "JPTYO",
                "USNYC",
                FUTURE_DATE,
                CargoType.GENERAL_CARGO,
                BigDecimal.ZERO
        )).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("重量");
    }

    @Test
    @DisplayName("weightKg が負の場合は例外を投げる")
    void rejectNegativeWeightKg() {
        BigDecimal negativeWeight = new BigDecimal("-10.0");
        assertThatThrownBy(() -> createCondition(
                "JPTYO",
                "USNYC",
                FUTURE_DATE,
                CargoType.GENERAL_CARGO,
                negativeWeight
        )).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("重量");
    }

    @Test
    @DisplayName("equals と hashCode が正しく動作する")
    void equalsAndHashCode() {
        QuoteCondition a = createCondition("JPTYO", "USNYC", FUTURE_DATE, CargoType.GENERAL_CARGO, DEFAULT_WEIGHT);
        QuoteCondition b = createCondition("JPTYO", "USNYC", FUTURE_DATE, CargoType.GENERAL_CARGO, DEFAULT_WEIGHT);

        assertThat(a).isEqualTo(b).hasSameHashCodeAs(b);
    }

    private QuoteCondition createCondition(
            String originLocode,
            String destinationLocode,
            LocalDate requestedArrivalDate,
            CargoType cargoType,
            BigDecimal weightKg
    ) {
        return new QuoteCondition(originLocode, destinationLocode, requestedArrivalDate, cargoType, weightKg);
    }
}
