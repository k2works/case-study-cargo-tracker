package com.example.cargotracker.booking.domain.model.aggregates;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.cargotracker.booking.domain.model.commands.AssignRouteCommand;
import com.example.cargotracker.booking.domain.model.events.CargoBookedEvent;
import com.example.cargotracker.booking.domain.model.events.CargoRoutedEvent;
import com.example.cargotracker.booking.domain.model.events.CargoSpecificationUpdatedEvent;
import com.example.cargotracker.booking.domain.model.events.RoutingRequestedEvent;
import com.example.cargotracker.booking.domain.model.valueobjects.CargoItinerary;
import com.example.cargotracker.booking.domain.model.valueobjects.CargoSpecification;
import com.example.cargotracker.booking.domain.model.valueobjects.CargoType;
import com.example.cargotracker.booking.domain.model.valueobjects.Dimensions;
import com.example.cargotracker.booking.domain.model.valueobjects.Leg;
import com.example.cargotracker.booking.domain.model.valueobjects.Weight;
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
 * 経路の確定（US09）。Cargo 集約の不変条件 5 を見る。
 *
 * <p>{@code CargoTest} から切り出した。受付・引き渡し・修正と、経路の確定は
 * 別の関心で、1 つのクラスに積むと何を確かめているのか読めなくなる。</p>
 */
class CargoRoutingTest {

    /** 業務タイムゾーン。期限は日付なので、どの時間帯で日付にするかが結果を変える。 */
    private static final ZoneId ZONE = ZoneId.of("Asia/Tokyo");
    private static final Instant ASSIGNED_AT = Instant.parse("2026-09-05T00:00:00Z");
    private static final LocalDate DEADLINE = LocalDate.of(2026, Month.DECEMBER, 1);

    private static final Instant LEG_LOAD = Instant.parse("2026-09-10T00:00:00Z");
    private static final Instant LEG_UNLOAD = Instant.parse("2026-09-24T09:00:00Z");

    private AxonTestFixture fixture;

    @BeforeEach
    void setUp() {
        EventSourcingConfigurer configurer = EventSourcingConfigurer.create()
                .registerEntity(EventSourcedEntityModule.autodetected(String.class, Cargo.class))
                // 「いつ確定したか」は集約が時計から採る。期限の比較も同じ時計の
                // タイムゾーンで行う（UTC で判断すると「期限当日」がずれる）。
                .componentRegistry(registry -> registry.registerComponent(
                        Clock.class, c -> Clock.fixed(ASSIGNED_AT, ZONE)));
        fixture = AxonTestFixture.with(configurer, c -> c.disableAxonServer());
    }

    private static CargoSpecification general() {
        return new CargoSpecification(CargoType.GENERAL, Weight.ofKilograms("1200"),
                Dimensions.of("120", "80", "100"), 10, "自動車部品", null, null);
    }

    private static CargoBookedEvent booked() {
        return new CargoBookedEvent("B-0001", "SHP-000001", "JPTYO", "USNYC", DEADLINE,
                "GENERAL", new BigDecimal("1200"), new BigDecimal("120"),
                new BigDecimal("80"), new BigDecimal("100"), 10, "自動車部品",
                null, null, null, null, "sales01");
    }

    private static Leg leg(String from, String to, Instant load, Instant unload) {
        return new Leg("V-MOL-001", Location.of(from), Location.of(to), load, unload);
    }

    private static CargoItinerary itinerary() {
        return new CargoItinerary(List.of(leg("JPTYO", "USNYC", LEG_LOAD, LEG_UNLOAD)));
    }

    private static AssignRouteCommand assign(CargoItinerary itinerary) {
        return new AssignRouteCommand("B-0001", itinerary, "routing01");
    }

    private static CargoRoutedEvent routed() {
        return new CargoRoutedEvent("B-0001",
                List.of(new CargoRoutedEvent.Leg("V-MOL-001", "JPTYO", "USNYC",
                        LEG_LOAD, LEG_UNLOAD)),
                "routing01", ASSIGNED_AT);
    }

    private static CargoSpecificationUpdatedEvent corrected(String destination,
            LocalDate deadline) {
        return new CargoSpecificationUpdatedEvent("B-0001", "JPTYO", destination, deadline,
                "GENERAL", new BigDecimal("1200"), new BigDecimal("120"),
                new BigDecimal("80"), new BigDecimal("100"), 10, "自動車部品",
                null, null, null, null, "sales02", ASSIGNED_AT);
    }

    @Test
    @DisplayName("US09: 引き渡した予約に経路を確定できる")
    void assignsRoute() {
        fixture.given().events(booked(), new RoutingRequestedEvent("B-0001", "sales01"))
                .when().command(assign(itinerary()))
                .then().success()
                .events(routed());
    }

    @Test
    @DisplayName("不変条件 5: 期限を満たさない旅程は断る（画面の検査を通さない経路でも）")
    void rejectsItineraryThatMissesTheDeadline() {
        // DEADLINE は 2026-12-01。それを過ぎて着く旅程を送る。
        var late = new CargoItinerary(
                List.of(leg("JPTYO", "USNYC", LEG_LOAD,
                        Instant.parse("2026-12-05T09:00:00Z"))));

        fixture.given().events(booked(), new RoutingRequestedEvent("B-0001", "sales01"))
                .when().command(assign(late))
                .then().exceptionSatisfies(e ->
                        assertThat(e.getMessage()).contains("期限"));
    }

    @Test
    @DisplayName("不変条件 5: 端点が予約と違う旅程は断る")
    void rejectsItineraryWithWrongEndpoints() {
        var wrong = new CargoItinerary(
                List.of(leg("JPOSA", "USNYC", LEG_LOAD, LEG_UNLOAD)));

        fixture.given().events(booked(), new RoutingRequestedEvent("B-0001", "sales01"))
                .when().command(assign(wrong))
                .then().exceptionSatisfies(e ->
                        assertThat(e.getMessage()).contains("経路仕様"));
    }

    @Test
    @DisplayName("引き渡していない予約には経路を確定できない")
    void rejectsRouteBeforeRoutingRequested() {
        fixture.given().event(booked())
                .when().command(assign(itinerary()))
                .then().exceptionSatisfies(e ->
                        assertThat(e.getMessage()).contains("経路"));
    }

    @Test
    @DisplayName("受け付けていない予約には経路を確定できない")
    void rejectsRouteForUnknownBooking() {
        fixture.given().noPriorActivity()
                .when().command(assign(itinerary()))
                .then().exceptionSatisfies(e ->
                        assertThat(e.getMessage()).contains("受け付けていません"));
    }

    @Test
    @DisplayName("US09: 経路が決まっても予約の状態は動かさない（BookingStatus を含むイベントを出さない）")
    void assigningRouteDoesNotChangeBookingStatus() {
        // 荷主に通知するまでは提案中（通知は US12・IT6）。ここで確定にすると、
        // 荷主が知らないうちに予約が確定したことになる。出るイベントは 1 本だけ。
        fixture.given().events(booked(), new RoutingRequestedEvent("B-0001", "sales01"))
                .when().command(assign(itinerary()))
                .then().success()
                .events(routed());
    }

    @Test
    @DisplayName("経路が決まった予約にもう一度経路を確定できない（戻してから設計し直す）")
    void rejectsSecondAssignment() {
        // 何度でも通すと、同じ予約に別の旅程が積み上がる。設計をやり直すには
        // 経路設計へ戻す（US11・IT6）。
        fixture.given().events(booked(), new RoutingRequestedEvent("B-0001", "sales01"),
                        routed())
                .when().command(assign(itinerary()))
                // 断りの文に列挙名（ROUTED）は出さない。業務担当者が読む文にする。
                .then().exceptionSatisfies(e ->
                        assertThat(e.getMessage()).contains("設計済").doesNotContain("ROUTED"));
    }

    @Test
    @DisplayName("期限を修正したあとは、新しい期限で旅程を判断する")
    void usesTheUpdatedDeadline() {
        // 修正で期限を縮めたのに古い期限で通すと、間に合わない経路が確定する。
        var narrowed = corrected("USNYC", LocalDate.of(2026, 9, 20));

        fixture.given().events(booked(), narrowed,
                        new RoutingRequestedEvent("B-0001", "sales01"))
                .when().command(assign(itinerary()))
                .then().exceptionSatisfies(e ->
                        assertThat(e.getMessage()).contains("期限"));
    }

    @Test
    @DisplayName("端点を修正したあとは、新しい端点で旅程を判断する")
    void usesTheUpdatedEndpoints() {
        var rerouted = corrected("GBLON", DEADLINE);

        fixture.given().events(booked(), rerouted,
                        new RoutingRequestedEvent("B-0001", "sales01"))
                .when().command(assign(itinerary()))
                .then().exceptionSatisfies(e ->
                        assertThat(e.getMessage()).contains("経路仕様"));
    }
}
