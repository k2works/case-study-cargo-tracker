package com.example.cargotracker.booking.domain;

import com.example.cargotracker.booking.domain.model.valueobjects.BookingLeg;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.*;

@DisplayName("BookingLeg 値オブジェクト")
class BookingLegTest {

    private static final String VOY = "VOY-001";
    private static final String ORIGIN = "JPTYO";
    private static final String DEST = "SGSIN";
    private static final LocalDate DEP = LocalDate.of(2026, 8, 1);
    private static final LocalDate ARR = LocalDate.of(2026, 8, 10);

    @Test
    @DisplayName("正常な区間情報で BookingLeg を生成できる")
    void createBookingLeg() {
        BookingLeg leg = new BookingLeg(VOY, ORIGIN, DEST, DEP, ARR, 0);

        assertThat(leg.voyageNumber()).isEqualTo(VOY);
        assertThat(leg.originLocode()).isEqualTo(ORIGIN);
        assertThat(leg.destinationLocode()).isEqualTo(DEST);
        assertThat(leg.departureDate()).isEqualTo(DEP);
        assertThat(leg.arrivalDate()).isEqualTo(ARR);
        assertThat(leg.legOrder()).isZero();
    }

    @Test
    @DisplayName("voyageNumber が null の場合は例外")
    void rejectNullVoyageNumber() {
        assertThatThrownBy(() -> new BookingLeg(null, ORIGIN, DEST, DEP, ARR, 0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("航海番号");
    }

    @Test
    @DisplayName("voyageNumber が空白の場合は例外")
    void rejectBlankVoyageNumber() {
        assertThatThrownBy(() -> new BookingLeg("  ", ORIGIN, DEST, DEP, ARR, 0))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("originLocode が null の場合は例外")
    void rejectNullOriginLocode() {
        assertThatThrownBy(() -> new BookingLeg(VOY, null, DEST, DEP, ARR, 0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("出発港");
    }

    @Test
    @DisplayName("destinationLocode が null の場合は例外")
    void rejectNullDestinationLocode() {
        assertThatThrownBy(() -> new BookingLeg(VOY, ORIGIN, null, DEP, ARR, 0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("到着港");
    }

    @Test
    @DisplayName("departureDate が null の場合は例外")
    void rejectNullDepartureDate() {
        assertThatThrownBy(() -> new BookingLeg(VOY, ORIGIN, DEST, null, ARR, 0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("出発日");
    }

    @Test
    @DisplayName("arrivalDate が null の場合は例外")
    void rejectNullArrivalDate() {
        assertThatThrownBy(() -> new BookingLeg(VOY, ORIGIN, DEST, DEP, null, 0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("到着日");
    }

    @Test
    @DisplayName("arrivalDate が departureDate より前の場合は例外")
    void rejectArrivalBeforeDeparture() {
        var late = LocalDate.of(2026, 8, 10);
        var early = LocalDate.of(2026, 8, 1);
        assertThatThrownBy(() -> new BookingLeg(VOY, ORIGIN, DEST, late, early, 0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("到着日");
    }

    @Test
    @DisplayName("legOrder が負の場合は例外")
    void rejectNegativeLegOrder() {
        assertThatThrownBy(() -> new BookingLeg(VOY, ORIGIN, DEST, DEP, ARR, -1))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("区間順序");
    }
}
