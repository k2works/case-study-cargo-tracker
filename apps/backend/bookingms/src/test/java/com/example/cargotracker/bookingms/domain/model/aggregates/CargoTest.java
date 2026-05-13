package com.example.cargotracker.bookingms.domain.model.aggregates;

import com.example.cargotracker.bookingms.domain.model.commands.BookCargoCommand;
import com.example.cargotracker.bookingms.domain.model.events.CargoBookedEvent;
import com.example.cargotracker.bookingms.domain.model.valueobjects.BookingStatus;
import com.example.cargotracker.bookingms.domain.model.valueobjects.CargoSpecification;
import com.example.cargotracker.bookingms.domain.model.valueobjects.Dimensions;
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
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

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
}
