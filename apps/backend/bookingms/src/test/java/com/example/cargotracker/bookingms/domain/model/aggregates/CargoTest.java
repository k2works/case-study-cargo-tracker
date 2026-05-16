package com.example.cargotracker.bookingms.domain.model.aggregates;

import com.example.cargotracker.bookingms.domain.model.commands.AssignRouteToCargoCommand;
import com.example.cargotracker.bookingms.domain.model.commands.BookCargoCommand;
import com.example.cargotracker.bookingms.domain.model.commands.ConfirmBookingCommand;
import com.example.cargotracker.bookingms.domain.model.commands.HandOffToRoutingCommand;
import com.example.cargotracker.bookingms.domain.model.commands.IssueTrackingNumberCommand;
import com.example.cargotracker.bookingms.domain.model.events.BookingConfirmedEvent;
import com.example.cargotracker.bookingms.domain.model.events.CargoBookedEvent;
import com.example.cargotracker.bookingms.domain.model.events.CargoHandedOffToRoutingEvent;
import com.example.cargotracker.bookingms.domain.model.events.CargoRoutedEvent;
import com.example.cargotracker.bookingms.domain.model.events.TrackingNumberIssuedEvent;
import com.example.cargotracker.bookingms.domain.model.valueobjects.BookingStatus;
import com.example.cargotracker.bookingms.domain.model.valueobjects.CargoItinerary;
import com.example.cargotracker.bookingms.domain.model.valueobjects.CargoSpecification;
import com.example.cargotracker.bookingms.domain.model.valueobjects.Dimensions;
import com.example.cargotracker.bookingms.domain.model.valueobjects.Leg;
import com.example.cargotracker.bookingms.domain.model.valueobjects.Location;
import com.example.cargotracker.bookingms.domain.model.valueobjects.RouteSpecification;
import com.example.cargotracker.bookingms.domain.model.valueobjects.RoutingStatus;
import com.example.cargotracker.bookingms.domain.model.valueobjects.ShipperId;
import org.axonframework.messaging.eventhandling.gateway.EventAppender;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

/**
 * Cargo Aggregate のユニットテスト。
 *
 * <p>{@code AxonTestFixture.with(Class)} の API は本プロジェクトの Axon 5.1 では
 * {@code ApplicationConfigurer} を要求し、{@code Class} を受け取らないため、ADR-0007 で
 * 想定したシグネチャと一致しなかった。フル機能の Aggregate Test は IT3 以降で
 * 統合テスト寄りに整備し、Round 2 では Mockito の {@link EventAppender} モックで
 * Command Handler と {@code @EventSourcingHandler} を直接検証する。</p>
 */
@DisplayName("Cargo Aggregate（ユニット）")
class CargoTest {

    private BookCargoCommand validCommand(String bookingId) {
        var spec = CargoSpecification.general(
                new BigDecimal("100"), new Dimensions(100, 100, 100), 1, "産業機械");
        var route = new RouteSpecification(
                Location.of("JPYOK"), Location.of("USLAX"), LocalDate.of(2026, 12, 31));
        return new BookCargoCommand(bookingId, new ShipperId(1L), spec, route);
    }

    @Test
    @DisplayName("book は EventAppender に CargoBookedEvent を追加する")
    void book_イベント追加() {
        String bookingId = UUID.randomUUID().toString();
        EventAppender appender = mock(EventAppender.class);
        var command = validCommand(bookingId);

        String returned = Cargo.book(command, appender);

        assertThat(returned).isEqualTo(bookingId);

        ArgumentCaptor<Object> captor = ArgumentCaptor.forClass(Object.class);
        verify(appender).append(captor.capture());
        assertThat(captor.getValue()).isInstanceOf(CargoBookedEvent.class);
        CargoBookedEvent event = (CargoBookedEvent) captor.getValue();
        assertThat(event.bookingId()).isEqualTo(bookingId);
        assertThat(event.shipperId()).isEqualTo(command.shipperId());
        assertThat(event.cargoSpec()).isEqualTo(command.cargoSpec());
        assertThat(event.routeSpec()).isEqualTo(command.routeSpec());
    }

    @Test
    @DisplayName("CargoBookedEvent を再生すると PRELIMINARY / NOT_ROUTED で復元される")
    void on_イベント再生で状態復元() {
        var cargo = new Cargo();
        String bookingId = UUID.randomUUID().toString();
        var spec = CargoSpecification.general(
                new BigDecimal("100"), new Dimensions(100, 100, 100), 1, "産業機械");
        var route = new RouteSpecification(
                Location.of("JPYOK"), Location.of("USLAX"), LocalDate.of(2026, 12, 31));
        var event = new CargoBookedEvent(bookingId, new ShipperId(1L), spec, route);

        cargo.on(event);

        assertThat(cargo.getBookingId()).isEqualTo(bookingId);
        assertThat(cargo.getBookingStatus()).isEqualTo(BookingStatus.PRELIMINARY);
        assertThat(cargo.getRoutingStatus()).isEqualTo(RoutingStatus.NOT_ROUTED);
    }

    private Cargo preliminaryCargo(String bookingId) {
        var cargo = new Cargo();
        var spec = CargoSpecification.general(
                new BigDecimal("100"), new Dimensions(100, 100, 100), 1, "産業機械");
        var route = new RouteSpecification(
                Location.of("JPYOK"), Location.of("USLAX"), LocalDate.of(2026, 12, 31));
        cargo.on(new CargoBookedEvent(bookingId, new ShipperId(1L), spec, route));
        return cargo;
    }

    @Test
    @DisplayName("handOffToRouting は EventAppender に CargoHandedOffToRoutingEvent を追加する")
    void handOff_イベント追加() {
        String bookingId = UUID.randomUUID().toString();
        Cargo cargo = preliminaryCargo(bookingId);
        EventAppender appender = mock(EventAppender.class);

        cargo.handOffToRouting(new HandOffToRoutingCommand(bookingId), appender);

        ArgumentCaptor<Object> captor = ArgumentCaptor.forClass(Object.class);
        verify(appender).append(captor.capture());
        assertThat(captor.getValue()).isInstanceOf(CargoHandedOffToRoutingEvent.class);
        CargoHandedOffToRoutingEvent event = (CargoHandedOffToRoutingEvent) captor.getValue();
        assertThat(event.bookingId()).isEqualTo(bookingId);
    }

    @Test
    @DisplayName("CargoHandedOffToRoutingEvent を再生すると ROUTING に遷移する")
    void on_引き渡しで状態遷移() {
        String bookingId = UUID.randomUUID().toString();
        Cargo cargo = preliminaryCargo(bookingId);

        cargo.on(new CargoHandedOffToRoutingEvent(bookingId));

        assertThat(cargo.getBookingStatus()).isEqualTo(BookingStatus.ROUTING);
    }

    @Test
    @DisplayName("PRELIMINARY 以外の状態では handOffToRouting は IllegalStateException")
    void handOff_PRELIMINARY以外は拒否() {
        String bookingId = UUID.randomUUID().toString();
        Cargo cargo = preliminaryCargo(bookingId);
        cargo.on(new CargoHandedOffToRoutingEvent(bookingId)); // PRELIMINARY -> ROUTING

        EventAppender appender = mock(EventAppender.class);

        var cmd = new HandOffToRoutingCommand(bookingId);
        assertThatThrownBy(() -> cargo.handOffToRouting(cmd, appender))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("PRELIMINARY");
        verifyNoInteractions(appender);
    }

    private Cargo routingCargo(String bookingId) {
        Cargo cargo = preliminaryCargo(bookingId);
        cargo.on(new CargoHandedOffToRoutingEvent(bookingId));
        return cargo;
    }

    @Test
    @DisplayName("US11: assignRoute は CargoRoutedEvent を追加し ROUTE_PROPOSED に遷移する")
    void assignRoute_イベント追加とROUTE_PROPOSED遷移() {
        String bookingId = UUID.randomUUID().toString();
        Cargo cargo = routingCargo(bookingId);
        EventAppender appender = mock(EventAppender.class);

        var leg = new Leg("V001", Location.of("JPYOK"), Location.of("USLAX"),
                LocalDateTime.of(2099, 7, 1, 9, 0), LocalDateTime.of(2099, 7, 15, 18, 0));
        var itinerary = new CargoItinerary(List.of(leg));
        var cmd = new AssignRouteToCargoCommand(bookingId, itinerary);

        cargo.assignRoute(cmd, appender);

        ArgumentCaptor<Object> captor = ArgumentCaptor.forClass(Object.class);
        verify(appender).append(captor.capture());
        assertThat(captor.getValue()).isInstanceOf(CargoRoutedEvent.class);
        CargoRoutedEvent event = (CargoRoutedEvent) captor.getValue();
        assertThat(event.bookingId()).isEqualTo(bookingId);
        assertThat(event.itinerary()).isEqualTo(itinerary);
    }

    @Test
    @DisplayName("US11: CargoRoutedEvent を再生すると ROUTE_PROPOSED に遷移する")
    void on_CargoRoutedEventでROUTE_PROPOSED遷移() {
        String bookingId = UUID.randomUUID().toString();
        Cargo cargo = routingCargo(bookingId);

        var leg = new Leg("V001", Location.of("JPYOK"), Location.of("USLAX"),
                LocalDateTime.of(2099, 7, 1, 9, 0), LocalDateTime.of(2099, 7, 15, 18, 0));
        var itinerary = new CargoItinerary(List.of(leg));
        cargo.on(new CargoRoutedEvent(bookingId, itinerary));

        assertThat(cargo.getBookingStatus()).isEqualTo(BookingStatus.ROUTE_PROPOSED);
    }

    @Test
    @DisplayName("US11: ROUTING 以外では assignRoute は IllegalStateException")
    void assignRoute_ROUTING以外は拒否() {
        String bookingId = UUID.randomUUID().toString();
        Cargo cargo = preliminaryCargo(bookingId); // PRELIMINARY state

        EventAppender appender = mock(EventAppender.class);
        var leg = new Leg("V001", Location.of("JPYOK"), Location.of("USLAX"),
                LocalDateTime.of(2099, 7, 1, 9, 0), LocalDateTime.of(2099, 7, 15, 18, 0));
        var itinerary = new CargoItinerary(List.of(leg));
        var cmd = new AssignRouteToCargoCommand(bookingId, itinerary);

        assertThatThrownBy(() -> cargo.assignRoute(cmd, appender))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("ROUTING");
        verifyNoInteractions(appender);
    }

    private Cargo routeProposedCargo(String bookingId) {
        Cargo cargo = routingCargo(bookingId);
        var leg = new Leg("V001", Location.of("JPYOK"), Location.of("USLAX"),
                LocalDateTime.of(2099, 7, 1, 9, 0), LocalDateTime.of(2099, 7, 15, 18, 0));
        cargo.on(new CargoRoutedEvent(bookingId, new CargoItinerary(List.of(leg))));
        return cargo;
    }

    @Test
    @DisplayName("US13: confirmBooking は BookingConfirmedEvent を追加し CONFIRMED に遷移する")
    void confirmBooking_イベント追加とCONFIRMED遷移() {
        String bookingId = UUID.randomUUID().toString();
        Cargo cargo = routeProposedCargo(bookingId);
        EventAppender appender = mock(EventAppender.class);

        cargo.confirmBooking(new ConfirmBookingCommand(bookingId), appender);

        ArgumentCaptor<Object> captor = ArgumentCaptor.forClass(Object.class);
        verify(appender).append(captor.capture());
        assertThat(captor.getValue()).isInstanceOf(BookingConfirmedEvent.class);
    }

    @Test
    @DisplayName("US13: BookingConfirmedEvent を再生すると CONFIRMED に遷移する")
    void on_BookingConfirmedEventでCONFIRMED遷移() {
        String bookingId = UUID.randomUUID().toString();
        Cargo cargo = routeProposedCargo(bookingId);

        cargo.on(new BookingConfirmedEvent(bookingId));

        assertThat(cargo.getBookingStatus()).isEqualTo(BookingStatus.CONFIRMED);
    }

    @Test
    @DisplayName("US14: issueTrackingNumber は TrackingNumberIssuedEvent を追加し TRACKING_ISSUED に遷移する")
    void issueTrackingNumber_イベント追加とTRACKING_ISSUED遷移() {
        String bookingId = UUID.randomUUID().toString();
        Cargo cargo = routeProposedCargo(bookingId);
        cargo.on(new BookingConfirmedEvent(bookingId)); // CONFIRMED state

        EventAppender appender = mock(EventAppender.class);
        cargo.issueTrackingNumber(new IssueTrackingNumberCommand(bookingId), appender);

        ArgumentCaptor<Object> captor = ArgumentCaptor.forClass(Object.class);
        verify(appender).append(captor.capture());
        assertThat(captor.getValue()).isInstanceOf(TrackingNumberIssuedEvent.class);
        TrackingNumberIssuedEvent event = (TrackingNumberIssuedEvent) captor.getValue();
        assertThat(event.trackingNumber()).startsWith("TRK-");
    }

    @Test
    @DisplayName("US14: TrackingNumberIssuedEvent を再生すると TRACKING_ISSUED に遷移する")
    void on_TrackingNumberIssuedEventでTRACKING_ISSUED遷移() {
        String bookingId = UUID.randomUUID().toString();
        Cargo cargo = routeProposedCargo(bookingId);
        cargo.on(new BookingConfirmedEvent(bookingId));

        cargo.on(new TrackingNumberIssuedEvent(bookingId, "TRK-20990701-ABCD1234"));

        assertThat(cargo.getBookingStatus()).isEqualTo(BookingStatus.TRACKING_ISSUED);
        assertThat(cargo.getTrackingNumber()).isEqualTo("TRK-20990701-ABCD1234");
    }
}
