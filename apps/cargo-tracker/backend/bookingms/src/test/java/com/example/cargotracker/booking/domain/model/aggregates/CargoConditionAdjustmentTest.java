package com.example.cargotracker.booking.domain.model.aggregates;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.cargotracker.booking.domain.model.commands.AdjustRouteSpecificationCommand;
import com.example.cargotracker.booking.domain.model.commands.AssignRouteCommand;
import com.example.cargotracker.booking.domain.model.events.CargoBookedEvent;
import com.example.cargotracker.booking.domain.model.events.CargoRoutedEvent;
import com.example.cargotracker.booking.domain.model.events.RouteSpecificationAdjustedEvent;
import com.example.cargotracker.booking.domain.model.events.RoutingRequestedEvent;
import com.example.cargotracker.booking.domain.model.valueobjects.CargoItinerary;
import com.example.cargotracker.booking.domain.model.valueobjects.Leg;
import com.example.cargotracker.shared.domain.location.Location;
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
 * 経路条件の調整（UC08 / US10）。
 *
 * <p>{@code CargoRoutingTest} から切り出した。経路を確定することと、組めない条件を
 * 調整し直すことは別の関心である。</p>
 */
class CargoConditionAdjustmentTest {

    private static final ZoneId ZONE = ZoneId.of("Asia/Tokyo");
    private static final Instant NOW = Instant.parse("2026-09-06T00:00:00Z");
    private static final LocalDate DEADLINE = LocalDate.of(2026, Month.DECEMBER, 1);
    private static final LocalDate EXTENDED = LocalDate.of(2027, Month.JANUARY, 31);

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

    private static AdjustRouteSpecificationCommand adjust(LocalDate deadline) {
        return new AdjustRouteSpecificationCommand("B-0001", deadline,
                List.of("SGSIN"), "JPOSA", "routing01");
    }

    private static CargoRoutedEvent routed() {
        return new CargoRoutedEvent("B-0001",
                List.of(new CargoRoutedEvent.Leg("V-MOL-001", "JPTYO", "USNYC",
                        Instant.parse("2026-09-10T00:00:00Z"),
                        Instant.parse("2026-09-24T09:00:00Z"))),
                "routing01", NOW);
    }

    @Test
    @DisplayName("US10: 引き渡した予約の条件を調整できる（誰がいつ何に変えたかが残る）")
    void adjustsCondition() {
        fixture.given().events(booked(), new RoutingRequestedEvent("B-0001", "sales01"))
                .when().command(adjust(EXTENDED))
                .then().success()
                .events(new RouteSpecificationAdjustedEvent("B-0001", EXTENDED,
                        List.of("SGSIN"), "JPOSA", "routing01", NOW));
    }

    @Test
    @DisplayName("US10: 調整した期限で旅程を判断する（延ばした期限で通る）")
    void usesTheAdjustedDeadline() {
        // 延ばす前は期限切れで断られる旅程。
        var late = new CargoItinerary(List.of(new Leg("V-1",
                Location.of("JPTYO"), Location.of("USNYC"),
                Instant.parse("2026-11-20T00:00:00Z"),
                Instant.parse("2026-12-20T00:00:00Z"))));

        fixture.given().events(booked(), new RoutingRequestedEvent("B-0001", "sales01"),
                        new RouteSpecificationAdjustedEvent("B-0001", EXTENDED,
                                List.of(), null, "routing01", NOW))
                .when().command(new AssignRouteCommand("B-0001", late, "routing01"))
                .then().success();
    }

    @Test
    @DisplayName("US10: 経路が決まった予約の条件を調整すると、設計し直しになる")
    void adjustingAfterRoutingReopensDesign() {
        // **確定済みの旅程は消さない**（再設計で入れ替わるまで残す）。戻すのは
        // routingStatus だけ。ここが ROUTED のままだと、条件を変えても
        // 経路設計者がもう一度確定できない。
        fixture.given().events(booked(), new RoutingRequestedEvent("B-0001", "sales01"),
                        routed(), new RouteSpecificationAdjustedEvent("B-0001", EXTENDED,
                                List.of(), null, "routing01", NOW))
                .when().command(new AssignRouteCommand("B-0001", new CargoItinerary(
                        List.of(new Leg("V-2", Location.of("JPTYO"), Location.of("USNYC"),
                                Instant.parse("2026-10-01T00:00:00Z"),
                                Instant.parse("2026-10-20T00:00:00Z")))), "routing01"))
                .then().success();
    }

    @Test
    @DisplayName("引き渡していない予約の条件は調整できない")
    void rejectsAdjustmentBeforeRoutingRequested() {
        // 仮受付のあいだは S24（予約修正）が正典。2 つの入口を同時に開かない。
        fixture.given().event(booked())
                .when().command(adjust(EXTENDED))
                .then().exceptionSatisfies(e ->
                        assertThat(e.getMessage()).contains("調整できません"));
    }

    @Test
    @DisplayName("受け付けていない予約の条件は調整できない")
    void rejectsAdjustmentForUnknownBooking() {
        fixture.given().noPriorActivity()
                .when().command(adjust(EXTENDED))
                .then().exceptionSatisfies(e ->
                        assertThat(e.getMessage()).contains("受け付けていません"));
    }

    @Test
    @DisplayName("到着期限を過去にはできない（組めない条件を作らせない）")
    void rejectsDeadlineInThePast() {
        fixture.given().events(booked(), new RoutingRequestedEvent("B-0001", "sales01"))
                .when().command(adjust(LocalDate.of(2026, Month.SEPTEMBER, 5)))
                .then().exceptionSatisfies(e ->
                        assertThat(e.getMessage()).contains("到着期限"));
    }

    @Test
    @DisplayName("出発地・目的地を除外港にはできない（必ず通る港を外させない）")
    void rejectsExcludingTheEndpoints() {
        fixture.given().events(booked(), new RoutingRequestedEvent("B-0001", "sales01"))
                .when().command(new AdjustRouteSpecificationCommand("B-0001", EXTENDED,
                        List.of("USNYC"), null, "routing01"))
                .then().exceptionSatisfies(e ->
                        assertThat(e.getMessage()).contains("除外"));
    }
}
