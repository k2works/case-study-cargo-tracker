package com.example.cargotracker.routing;

import com.example.cargotracker.routing.domain.model.CargoType;
import com.example.cargotracker.routing.domain.model.RouteDesignCondition;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("RouteDesignCondition")
class RouteDesignConditionTest {

    @Test
    @DisplayName("全フィールドが揃っている場合 isComplete() は true を返す")
    void isComplete_全フィールド有り() {
        var condition = new RouteDesignCondition(
            UUID.randomUUID(),
            "JPTYO",
            "SGSIN",
            LocalDate.of(2026, 6, 30),
            CargoType.GENERAL,
            new BigDecimal("500.0")
        );
        assertThat(condition.isComplete()).isTrue();
    }

    @Test
    @DisplayName("originLocode が null の場合 isComplete() は false を返す")
    void isComplete_originLocode_null() {
        var condition = new RouteDesignCondition(
            UUID.randomUUID(),
            null,
            "SGSIN",
            LocalDate.of(2026, 6, 30),
            CargoType.GENERAL,
            new BigDecimal("500.0")
        );
        assertThat(condition.isComplete()).isFalse();
    }

    @Test
    @DisplayName("destinationLocode が null の場合 isComplete() は false を返す")
    void isComplete_destinationLocode_null() {
        var condition = new RouteDesignCondition(
            UUID.randomUUID(),
            "JPTYO",
            null,
            LocalDate.of(2026, 6, 30),
            CargoType.GENERAL,
            new BigDecimal("500.0")
        );
        assertThat(condition.isComplete()).isFalse();
    }

    @Test
    @DisplayName("requestedArrivalDate が null の場合 isComplete() は false を返す")
    void isComplete_requestedArrivalDate_null() {
        var condition = new RouteDesignCondition(
            UUID.randomUUID(),
            "JPTYO",
            "SGSIN",
            null,
            CargoType.GENERAL,
            new BigDecimal("500.0")
        );
        assertThat(condition.isComplete()).isFalse();
    }

    @Test
    @DisplayName("cargoType が null の場合 isComplete() は false を返す")
    void isComplete_cargoType_null() {
        var condition = new RouteDesignCondition(
            UUID.randomUUID(),
            "JPTYO",
            "SGSIN",
            LocalDate.of(2026, 6, 30),
            null,
            new BigDecimal("500.0")
        );
        assertThat(condition.isComplete()).isFalse();
    }

    @Test
    @DisplayName("weightKg が null の場合 isComplete() は false を返す")
    void isComplete_weightKg_null() {
        var condition = new RouteDesignCondition(
            UUID.randomUUID(),
            "JPTYO",
            "SGSIN",
            LocalDate.of(2026, 6, 30),
            CargoType.GENERAL,
            null
        );
        assertThat(condition.isComplete()).isFalse();
    }

    @Test
    @DisplayName("weightKg が 0 以下の場合 isComplete() は false を返す")
    void isComplete_weightKg_zero() {
        var condition = new RouteDesignCondition(
            UUID.randomUUID(),
            "JPTYO",
            "SGSIN",
            LocalDate.of(2026, 6, 30),
            CargoType.GENERAL,
            BigDecimal.ZERO
        );
        assertThat(condition.isComplete()).isFalse();
    }

    @Test
    @DisplayName("bookingId が null の場合 IllegalArgumentException をスローする")
    void bookingId_null_throwsException() {
        LocalDate requestedArrivalDate = LocalDate.of(2026, 6, 30);
        BigDecimal weightKg = new BigDecimal("500.0");

        assertThatThrownBy(() -> new RouteDesignCondition(
            null,
            "JPTYO",
            "SGSIN",
            requestedArrivalDate,
            CargoType.GENERAL,
            weightKg
        )).isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("bookingId");
    }

    @Test
    @DisplayName("originLocode が空文字の場合 isComplete() は false を返す")
    void isComplete_originLocode_blank() {
        var condition = new RouteDesignCondition(
            UUID.randomUUID(),
            "   ",
            "SGSIN",
            LocalDate.of(2026, 6, 30),
            CargoType.GENERAL,
            new BigDecimal("500.0")
        );
        assertThat(condition.isComplete()).isFalse();
    }
}
