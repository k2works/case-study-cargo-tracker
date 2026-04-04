package com.example.cargotracker.routing;

import com.example.cargotracker.routing.domain.model.VoyageLeg;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("VoyageLeg")
class VoyageLegTest {

    @Test
    @DisplayName("有効な値で VoyageLeg を生成できる")
    void voyageLeg_生成_正常系() {
        var leg = new VoyageLeg("JPTYO", "SGSIN",
            LocalDate.of(2026, 6, 1), LocalDate.of(2026, 6, 15));

        assertThat(leg.originLocode()).isEqualTo("JPTYO");
        assertThat(leg.destinationLocode()).isEqualTo("SGSIN");
        assertThat(leg.departureDate()).isEqualTo(LocalDate.of(2026, 6, 1));
        assertThat(leg.arrivalDate()).isEqualTo(LocalDate.of(2026, 6, 15));
    }

    @Test
    @DisplayName("originLocode が null の場合 IllegalArgumentException をスローする")
    void voyageLeg_originLocode_null() {
        assertThatThrownBy(() -> new VoyageLeg(null, "SGSIN",
            LocalDate.of(2026, 6, 1), LocalDate.of(2026, 6, 15)))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("originLocode");
    }

    @Test
    @DisplayName("destinationLocode が null の場合 IllegalArgumentException をスローする")
    void voyageLeg_destinationLocode_null() {
        assertThatThrownBy(() -> new VoyageLeg("JPTYO", null,
            LocalDate.of(2026, 6, 1), LocalDate.of(2026, 6, 15)))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("destinationLocode");
    }

    @Test
    @DisplayName("departureDate が null の場合 IllegalArgumentException をスローする")
    void voyageLeg_departureDate_null() {
        assertThatThrownBy(() -> new VoyageLeg("JPTYO", "SGSIN", null, LocalDate.of(2026, 6, 15)))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("departureDate");
    }

    @Test
    @DisplayName("arrivalDate が null の場合 IllegalArgumentException をスローする")
    void voyageLeg_arrivalDate_null() {
        assertThatThrownBy(() -> new VoyageLeg("JPTYO", "SGSIN", LocalDate.of(2026, 6, 1), null))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("arrivalDate");
    }

    @Test
    @DisplayName("arrivalDate が departureDate より前の場合 IllegalArgumentException をスローする")
    void voyageLeg_arrivalDate_before_departureDate() {
        assertThatThrownBy(() -> new VoyageLeg("JPTYO", "SGSIN",
            LocalDate.of(2026, 6, 15), LocalDate.of(2026, 6, 1)))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("arrivalDate");
    }

    @Test
    @DisplayName("transitDays() は到着日 - 出発日の日数を返す")
    void voyageLeg_transitDays() {
        var leg = new VoyageLeg("JPTYO", "SGSIN",
            LocalDate.of(2026, 6, 1), LocalDate.of(2026, 6, 15));
        assertThat(leg.transitDays()).isEqualTo(14);
    }
}
