package com.example.cargotracker.booking.domain.model.aggregates;

import com.example.cargotracker.booking.domain.model.commands.ConfirmBookingCommand;
import com.example.cargotracker.booking.domain.model.events.BookingConfirmedEvent;
import com.example.cargotracker.booking.domain.model.events.CargoBookedEvent;
import com.example.cargotracker.booking.domain.model.events.CargoRoutedEvent;
import com.example.cargotracker.booking.domain.model.events.RoutingRequestedEvent;
import com.example.cargotracker.booking.domain.model.events.ShipperNotifiedEvent;
import com.example.cargotracker.shared.domain.error.IllegalTransition;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.Month;
import java.time.ZoneId;
import java.util.List;
import org.axonframework.eventsourcing.configuration.EventSourcedEntityModule;
import org.axonframework.eventsourcing.configuration.EventSourcingConfigurer;
import org.axonframework.test.fixture.AxonTestFixture;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 予約の確定（UC11 / US13）。
 *
 * <p>中核の判断は<b>「通知していない予約は確定できない」</b>。荷主が知らない
 * うちに確定すると、追跡番号の発行と輸送手配まで進んでしまう。</p>
 */
class CargoConfirmationTest {

    private static final ZoneId ZONE = ZoneId.of("Asia/Tokyo");
    private static final Instant NOW = Instant.parse("2026-09-06T00:00:00Z");
    private static final LocalDate DEADLINE = LocalDate.of(2026, Month.DECEMBER, 1);

    private AxonTestFixture fixture;

    @BeforeEach
    void setUp() {
        EventSourcingConfigurer configurer = EventSourcingConfigurer.create()
                .registerEntity(EventSourcedEntityModule.autodetected(String.class, Cargo.class))
                .componentRegistry(registry -> registry.registerComponent(
                        Clock.class, c -> Clock.fixed(NOW, ZONE)));
        fixture = AxonTestFixture.with(configurer, c -> c.disableAxonServer());
    }

    private static CargoBookedEvent booked() {
        return new CargoBookedEvent("B-0001", "SHP-000001", "JPTYO", "USNYC", DEADLINE,
                "GENERAL", new BigDecimal("1200"), new BigDecimal("120"),
                new BigDecimal("80"), new BigDecimal("100"), 10, "自動車部品",
                null, null, null, null, "sales01");
    }

    private static CargoRoutedEvent routed() {
        return new CargoRoutedEvent("B-0001",
                List.of(new CargoRoutedEvent.Leg("V-MOL-001", "JPTYO", "USNYC",
                        Instant.parse("2026-09-10T00:00:00Z"),
                        Instant.parse("2026-09-24T09:00:00Z"))),
                "routing01", NOW);
    }

    private static ShipperNotifiedEvent notified() {
        return new ShipperNotifiedEvent("B-0001", "shipper@example.com",
                "JPTYO → USNYC（1 区間）", "sales01", NOW);
    }

    @Test
    @DisplayName("US13 §2: 通知済みの予約を確定できる")
    void confirmsNotifiedBooking() {
        fixture.given().events(booked(), new RoutingRequestedEvent("B-0001", "sales01"),
                        routed(), notified())
                .when().command(new ConfirmBookingCommand("B-0001", "sales01"))
                .then().success()
                .events(new BookingConfirmedEvent("B-0001", "sales01", NOW));
    }

    @Test
    @DisplayName("US13: 通知していない予約は確定できない（荷主が知らないうちに進まない）")
    void rejectsUnnotifiedBooking() {
        // 経路は決まっているが、荷主へは伝えていない。承認は「通知した経路への
        // 承認」なので、通知が無ければ承認するものが無い。
        fixture.given().events(booked(), new RoutingRequestedEvent("B-0001", "sales01"),
                        routed())
                .when().command(new ConfirmBookingCommand("B-0001", "sales01"))
                .then().exception(IllegalTransition.class);
    }

    @Test
    @DisplayName("US13: 仮受付の予約は確定できない")
    void rejectsPreliminaryBooking() {
        fixture.given().events(booked())
                .when().command(new ConfirmBookingCommand("B-0001", "sales01"))
                .then().exception(IllegalTransition.class);
    }

    @Test
    @DisplayName("US13: 二重に確定しない（遷移表に CONFIRMED → CONFIRMED は無い）")
    void rejectsSecondConfirmation() {
        fixture.given().events(booked(), new RoutingRequestedEvent("B-0001", "sales01"),
                        routed(), notified(),
                        new BookingConfirmedEvent("B-0001", "sales01", NOW))
                .when().command(new ConfirmBookingCommand("B-0001", "sales02"))
                .then().exception(IllegalTransition.class);
    }

    @Test
    @DisplayName("受け付けていない予約は確定できない")
    void rejectsUnknownBooking() {
        fixture.given().noPriorActivity()
                .when().command(new ConfirmBookingCommand("B-NONE", "sales01"))
                .then().exception(IllegalTransition.class);
    }
}
