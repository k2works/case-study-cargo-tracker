package com.example.cargotracker.booking.domain;

import com.example.cargotracker.shipper.domain.ShipperId;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.*;

@DisplayName("Booking 集約")
class BookingTest {

    private BookingId anyBookingId() {
        return BookingId.generate();
    }

    private ShipperId anyShipperId() {
        return ShipperId.generate();
    }

    private CargoSpecification anyCargo() {
        return new CargoSpecification(
                CargoType.GENERAL_CARGO,
                new BigDecimal("100.0"),
                null, null, null,
                1, "テスト品");
    }

    private TransportCondition anyTransport() {
        return new TransportCondition(
                "JPTYO", "USNYC",
                LocalDate.of(2025, 8, 1),
                LocalDate.of(2025, 9, 1));
    }

    @Test
    @DisplayName("予約を登録できる")
    void registerBooking() {
        BookingId id = anyBookingId();
        ShipperId shipperId = anyShipperId();
        CargoSpecification cargo = anyCargo();
        TransportCondition transport = anyTransport();

        Booking booking = Booking.register(id, shipperId, cargo, transport);

        assertThat(booking.getId()).isEqualTo(id);
        assertThat(booking.getShipperId()).isEqualTo(shipperId);
        assertThat(booking.getCargoSpecification()).isEqualTo(cargo);
        assertThat(booking.getTransportCondition()).isEqualTo(transport);
        assertThat(booking.getStatus()).isEqualTo(BookingStatus.PROVISIONAL);
    }

    @Test
    @DisplayName("登録時に BookingRegisteredEvent が発行される")
    void registrationEmitsEvent() {
        BookingId id = anyBookingId();
        ShipperId shipperId = anyShipperId();

        Booking booking = Booking.register(id, shipperId, anyCargo(), anyTransport());

        assertThat(booking.getDomainEvents()).hasSize(1);
        assertThat(booking.getDomainEvents().get(0)).isInstanceOf(BookingRegisteredEvent.class);

        BookingRegisteredEvent event = (BookingRegisteredEvent) booking.getDomainEvents().get(0);
        assertThat(event.bookingId()).isEqualTo(id);
        assertThat(event.shipperId()).isEqualTo(shipperId);
    }

    @Test
    @DisplayName("id が null の場合は登録できない")
    void rejectNullId() {
        assertThatThrownBy(() -> Booking.register(null, anyShipperId(), anyCargo(), anyTransport()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("shipperId が null の場合は登録できない")
    void rejectNullShipperId() {
        assertThatThrownBy(() -> Booking.register(anyBookingId(), null, anyCargo(), anyTransport()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("cargoSpecification が null の場合は登録できない")
    void rejectNullCargoSpecification() {
        assertThatThrownBy(() -> Booking.register(anyBookingId(), anyShipperId(), null, anyTransport()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("transportCondition が null の場合は登録できない")
    void rejectNullTransportCondition() {
        assertThatThrownBy(() -> Booking.register(anyBookingId(), anyShipperId(), anyCargo(), null))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
