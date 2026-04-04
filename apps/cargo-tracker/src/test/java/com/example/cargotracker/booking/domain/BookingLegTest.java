package com.example.cargotracker.booking.domain;

import com.example.cargotracker.booking.domain.model.valueobjects.BookingLeg;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.*;

@DisplayName("BookingLeg 値オブジェクト")
class BookingLegTest {

    @Test
    @DisplayName("正常な区間情報で BookingLeg を生成できる")
    void createBookingLeg() {
        BookingLeg leg = new BookingLeg(
                "VOY-001",
                "JPTYO",
                "SGSIN",
                LocalDate.of(2026, 8, 1),
                LocalDate.of(2026, 8, 10),
                0
        );

        assertThat(leg.voyageNumber()).isEqualTo("VOY-001");
        assertThat(leg.originLocode()).isEqualTo("JPTYO");
        assertThat(leg.destinationLocode()).isEqualTo("SGSIN");
        assertThat(leg.departureDate()).isEqualTo(LocalDate.of(2026, 8, 1));
        assertThat(leg.arrivalDate()).isEqualTo(LocalDate.of(2026, 8, 10));
        assertThat(leg.legOrder()).isEqualTo(0);
    }

    @Test
    @DisplayName("voyageNumber が null の場合は例外")
    void rejectNullVoyageNumber() {
        assertThatThrownBy(() -> new BookingLeg(null, "JPTYO", "SGSIN",
                LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 10), 0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("航海番号");
    }

    @Test
    @DisplayName("voyageNumber が空白の場合は例外")
    void rejectBlankVoyageNumber() {
        assertThatThrownBy(() -> new BookingLeg("  ", "JPTYO", "SGSIN",
                LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 10), 0))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("originLocode が null の場合は例外")
    void rejectNullOriginLocode() {
        assertThatThrownBy(() -> new BookingLeg("VOY-001", null, "SGSIN",
                LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 10), 0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("出発港");
    }

    @Test
    @DisplayName("destinationLocode が null の場合は例外")
    void rejectNullDestinationLocode() {
        assertThatThrownBy(() -> new BookingLeg("VOY-001", "JPTYO", null,
                LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 10), 0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("到着港");
    }

    @Test
    @DisplayName("departureDate が null の場合は例外")
    void rejectNullDepartureDate() {
        assertThatThrownBy(() -> new BookingLeg("VOY-001", "JPTYO", "SGSIN",
                null, LocalDate.of(2026, 8, 10), 0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("出発日");
    }

    @Test
    @DisplayName("arrivalDate が null の場合は例外")
    void rejectNullArrivalDate() {
        assertThatThrownBy(() -> new BookingLeg("VOY-001", "JPTYO", "SGSIN",
                LocalDate.of(2026, 8, 1), null, 0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("到着日");
    }

    @Test
    @DisplayName("arrivalDate が departureDate より前の場合は例外")
    void rejectArrivalBeforeDeparture() {
        assertThatThrownBy(() -> new BookingLeg("VOY-001", "JPTYO", "SGSIN",
                LocalDate.of(2026, 8, 10), LocalDate.of(2026, 8, 1), 0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("到着日");
    }

    @Test
    @DisplayName("legOrder が負の場合は例外")
    void rejectNegativeLegOrder() {
        assertThatThrownBy(() -> new BookingLeg("VOY-001", "JPTYO", "SGSIN",
                LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 10), -1))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("区間順序");
    }
}
