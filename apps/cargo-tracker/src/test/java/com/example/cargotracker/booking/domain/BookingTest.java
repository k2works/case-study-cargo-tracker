package com.example.cargotracker.booking.domain;

import com.example.cargotracker.booking.domain.event.BookingConfirmedEvent;
import com.example.cargotracker.booking.domain.event.BookingRegisteredEvent;

import com.example.cargotracker.booking.domain.event.BookingRouteAssignedEvent;
import com.example.cargotracker.booking.domain.model.aggregates.Booking;
import com.example.cargotracker.booking.domain.model.aggregates.BookingId;
import com.example.cargotracker.booking.domain.model.valueobjects.AssignedRoute;
import com.example.cargotracker.booking.domain.model.valueobjects.BookingLeg;
import com.example.cargotracker.booking.domain.model.valueobjects.BookingStatus;
import com.example.cargotracker.booking.domain.model.valueobjects.CargoSpecification;
import com.example.cargotracker.booking.domain.model.valueobjects.CargoType;
import com.example.cargotracker.booking.domain.model.valueobjects.TransportCondition;
import com.example.cargotracker.shared.domain.model.ShipperId;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

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
        var shipperId = anyShipperId();
        var cargo = anyCargo();
        var transport = anyTransport();
        assertThatThrownBy(() -> Booking.register(null, shipperId, cargo, transport))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("shipperId が null の場合は登録できない")
    void rejectNullShipperId() {
        var bookingId = anyBookingId();
        var cargo = anyCargo();
        var transport = anyTransport();
        assertThatThrownBy(() -> Booking.register(bookingId, null, cargo, transport))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("cargoSpecification が null の場合は登録できない")
    void rejectNullCargoSpecification() {
        var bookingId = anyBookingId();
        var shipperId = anyShipperId();
        var transport = anyTransport();
        assertThatThrownBy(() -> Booking.register(bookingId, shipperId, null, transport))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("transportCondition が null の場合は登録できない")
    void rejectNullTransportCondition() {
        var bookingId = anyBookingId();
        var shipperId = anyShipperId();
        var cargo = anyCargo();
        assertThatThrownBy(() -> Booking.register(bookingId, shipperId, cargo, null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("ルートを割り当てられる")
    void assignRoute() {
        Booking booking = Booking.register(anyBookingId(), anyShipperId(), anyCargo(), anyTransport());
        AssignedRoute route = new AssignedRoute("VOY-001", "JPTYO/SGSIN/USNYC", LocalDate.of(2025, 9, 1));

        booking.assignRoute(route);

        assertThat(booking.getAssignedRoute()).isEqualTo(route);
    }

    @Test
    @DisplayName("ルート割り当て時に BookingRouteAssignedEvent が発行される")
    void assignRouteEmitsEvent() {
        Booking booking = Booking.register(anyBookingId(), anyShipperId(), anyCargo(), anyTransport());
        AssignedRoute route = new AssignedRoute("VOY-001", "JPTYO/SGSIN/USNYC", LocalDate.of(2025, 9, 1));

        booking.assignRoute(route);

        assertThat(booking.getDomainEvents()).hasSize(2); // register + assignRoute
        assertThat(booking.getDomainEvents().get(1)).isInstanceOf(BookingRouteAssignedEvent.class);
    }

    @Test
    @DisplayName("null ルートは割り当てできない")
    void rejectNullAssignedRoute() {
        Booking booking = Booking.register(anyBookingId(), anyShipperId(), anyCargo(), anyTransport());
        assertThatThrownBy(() -> booking.assignRoute(null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("登録直後はルートが未割り当て")
    void assignedRouteIsNullAfterRegister() {
        Booking booking = Booking.register(anyBookingId(), anyShipperId(), anyCargo(), anyTransport());
        assertThat(booking.getAssignedRoute()).isNull();
    }

    @Test
    @DisplayName("ルート割り当て済み予約を確定できる")
    void confirmBooking() {
        Booking booking = Booking.register(anyBookingId(), anyShipperId(), anyCargo(), anyTransport());
        booking.assignRoute(new AssignedRoute("VOY-001", "JPTYO/USNYC", LocalDate.of(2025, 9, 1)));

        booking.confirm();

        assertThat(booking.getStatus()).isEqualTo(BookingStatus.CONFIRMED);
    }

    @Test
    @DisplayName("予約確定時に BookingConfirmedEvent が発行される")
    void confirmEmitsEvent() {
        Booking booking = Booking.register(anyBookingId(), anyShipperId(), anyCargo(), anyTransport());
        booking.assignRoute(new AssignedRoute("VOY-001", "JPTYO/USNYC", LocalDate.of(2025, 9, 1)));

        booking.confirm();

        long confirmedEventCount = booking.getDomainEvents().stream()
                .filter(BookingConfirmedEvent.class::isInstance)
                .count();
        assertThat(confirmedEventCount).isEqualTo(1);
    }

    @Test
    @DisplayName("ルート未割り当ての予約は確定できない")
    void cannotConfirmWithoutRoute() {
        Booking booking = Booking.register(anyBookingId(), anyShipperId(), anyCargo(), anyTransport());

        assertThatThrownBy(booking::confirm)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("ルート");
    }

    @Test
    @DisplayName("既に確定済みの予約は再確定できない")
    void cannotConfirmAlreadyConfirmed() {
        Booking booking = Booking.register(anyBookingId(), anyShipperId(), anyCargo(), anyTransport());
        booking.assignRoute(new AssignedRoute("VOY-001", "JPTYO/USNYC", LocalDate.of(2025, 9, 1)));
        booking.confirm();

        assertThatThrownBy(booking::confirm)
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("確定済み予約を精算済みにできる")
    void settleBooking() {
        Booking booking = Booking.register(anyBookingId(), anyShipperId(), anyCargo(), anyTransport());
        booking.assignRoute(new AssignedRoute("VOY-001", "JPTYO/USNYC", LocalDate.of(2025, 9, 1)));
        booking.confirm();

        booking.settle();

        assertThat(booking.getStatus()).isEqualTo(BookingStatus.SETTLED);
    }

    @Test
    @DisplayName("区間詳細を含むルートを割り当てられる")
    void assignRouteWithLegs() {
        Booking booking = Booking.register(anyBookingId(), anyShipperId(), anyCargo(), anyTransport());
        AssignedRoute route = new AssignedRoute("VOY-001", "JPTYO/SGSIN/USNYC", LocalDate.of(2026, 9, 15));
        List<BookingLeg> legs = List.of(
                new BookingLeg("VOY-001", "JPTYO", "SGSIN",
                        LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 10), 0),
                new BookingLeg("VOY-001", "SGSIN", "USNYC",
                        LocalDate.of(2026, 8, 12), LocalDate.of(2026, 9, 15), 1)
        );

        booking.assignRouteWithLegs(route, legs);

        assertThat(booking.getAssignedRoute()).isEqualTo(route);
        assertThat(booking.getLegs()).hasSize(2);
        assertThat(booking.getLegs().get(0).originLocode()).isEqualTo("JPTYO");
        assertThat(booking.getLegs().get(1).destinationLocode()).isEqualTo("USNYC");
    }

    @Test
    @DisplayName("assignRouteWithLegs で null ルートは拒否される")
    void rejectNullRouteWithLegs() {
        Booking booking = Booking.register(anyBookingId(), anyShipperId(), anyCargo(), anyTransport());
        var emptyLegs = List.<BookingLeg>of();
        assertThatThrownBy(() -> booking.assignRouteWithLegs(null, emptyLegs))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("assignRouteWithLegs で null legs は拒否される")
    void rejectNullLegs() {
        Booking booking = Booking.register(anyBookingId(), anyShipperId(), anyCargo(), anyTransport());
        AssignedRoute route = new AssignedRoute("VOY-001", "JPTYO/USNYC", LocalDate.of(2026, 9, 15));
        assertThatThrownBy(() -> booking.assignRouteWithLegs(route, null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("assignRouteWithLegs で BookingRouteAssignedEvent が発行される")
    void assignRouteWithLegsEmitsEvent() {
        Booking booking = Booking.register(anyBookingId(), anyShipperId(), anyCargo(), anyTransport());
        AssignedRoute route = new AssignedRoute("VOY-001", "JPTYO/USNYC", LocalDate.of(2026, 9, 15));
        List<BookingLeg> legs = List.of(
                new BookingLeg("VOY-001", "JPTYO", "USNYC",
                        LocalDate.of(2026, 8, 1), LocalDate.of(2026, 9, 15), 0)
        );

        booking.assignRouteWithLegs(route, legs);

        assertThat(booking.getDomainEvents()).hasSize(2); // register + assignRouteWithLegs
        assertThat(booking.getDomainEvents().get(1)).isInstanceOf(BookingRouteAssignedEvent.class);
    }
}
