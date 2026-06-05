package com.example.bookingms.domain.model;

import com.example.bookingms.domain.commands.AssignRouteToCargoCommand;
import com.example.bookingms.domain.commands.AssignTrackingDetailsCommand;
import com.example.bookingms.domain.commands.BookCargoCommand;
import com.example.bookingms.domain.commands.CancelBookingCommand;
import com.example.bookingms.domain.commands.ConfirmBookingCommand;
import com.example.bookingms.domain.commands.MarkBookingSettledCommand;
import com.example.bookingms.domain.commands.NotifyRouteToShipperCommand;
import com.example.bookingms.domain.commands.RequestRouteDesignCommand;
import com.example.bookingms.domain.events.BookingCancelledEvent;
import com.example.bookingms.domain.events.BookingConfirmedEvent;
import com.example.bookingms.domain.events.BookingSettledEvent;
import com.example.bookingms.domain.events.CargoBookedEvent;
import com.example.bookingms.domain.events.CargoRoutedEvent;
import com.example.bookingms.domain.events.CargoTrackingAssignedEvent;
import com.example.bookingms.domain.events.RouteNotifiedToShipperEvent;
import com.example.shared.events.RouteDesignRequestedEvent;
import org.axonframework.test.aggregate.AggregateTestFixture;
import org.axonframework.test.aggregate.FixtureConfiguration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

class CargoAggregateTest {

    private FixtureConfiguration<Cargo> fixture;

    @BeforeEach
    void setUp() {
        fixture = new AggregateTestFixture<>(Cargo.class);
    }

    private BookCargoCommand validGeneralCommand(String bookingId) {
        return new BookCargoCommand(
                bookingId,
                "S-001",
                new RouteSpecification("JPTYO", "USNYC", LocalDate.of(2026, 9, 30)),
                new CargoSpecification(
                        CargoType.GENERAL,
                        new BigDecimal("1500.00"),
                        new Dimensions(120, 80, 60),
                        10,
                        "電子部品"));
    }

    @Test
    @DisplayName("US04: 一般貨物の予約を登録できる")
    void 一般貨物の予約を登録できる() {
        BookCargoCommand cmd = validGeneralCommand("B-001");
        fixture.givenNoPriorActivity()
                .when(cmd)
                .expectSuccessfulHandlerExecution()
                .expectEvents(new CargoBookedEvent(
                        "B-001",
                        "S-001",
                        cmd.routeSpec(),
                        cmd.cargoSpec(),
                        "PRELIMINARY",
                        "NOT_ROUTED"));
    }

    @Test
    @DisplayName("US04: 予約 ID が空文字列の場合は例外")
    void 予約IDが空文字列の場合は例外() {
        BookCargoCommand cmd = validGeneralCommand("");
        fixture.givenNoPriorActivity()
                .when(cmd)
                .expectException(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("US04: 荷主 ID が空文字列の場合は例外")
    void 荷主IDが空文字列の場合は例外() {
        BookCargoCommand cmd = new BookCargoCommand(
                "B-002",
                "",
                new RouteSpecification("JPTYO", "USNYC", LocalDate.of(2026, 9, 30)),
                new CargoSpecification(
                        CargoType.GENERAL,
                        new BigDecimal("1500.00"),
                        new Dimensions(120, 80, 60),
                        10,
                        "電子部品"));
        fixture.givenNoPriorActivity()
                .when(cmd)
                .expectException(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("US04: 出発地と目的地が同一の場合は例外")
    void 出発地と目的地が同一の場合は例外() {
        BookCargoCommand cmd = new BookCargoCommand(
                "B-003",
                "S-001",
                new RouteSpecification("JPTYO", "JPTYO", LocalDate.of(2026, 9, 30)),
                new CargoSpecification(
                        CargoType.GENERAL,
                        new BigDecimal("1500.00"),
                        new Dimensions(120, 80, 60),
                        10,
                        "電子部品"));
        fixture.givenNoPriorActivity()
                .when(cmd)
                .expectException(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("US04: 到着期限が過去日の場合は例外")
    void 到着期限が過去日の場合は例外() {
        BookCargoCommand cmd = new BookCargoCommand(
                "B-004",
                "S-001",
                new RouteSpecification("JPTYO", "USNYC", LocalDate.of(2020, 1, 1)),
                new CargoSpecification(
                        CargoType.GENERAL,
                        new BigDecimal("1500.00"),
                        new Dimensions(120, 80, 60),
                        10,
                        "電子部品"));
        fixture.givenNoPriorActivity()
                .when(cmd)
                .expectException(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("US04: 重量が 0 以下の場合は例外")
    void 重量がゼロ以下の場合は例外() {
        BookCargoCommand cmd = new BookCargoCommand(
                "B-005",
                "S-001",
                new RouteSpecification("JPTYO", "USNYC", LocalDate.of(2026, 9, 30)),
                new CargoSpecification(
                        CargoType.GENERAL,
                        BigDecimal.ZERO,
                        new Dimensions(120, 80, 60),
                        10,
                        "電子部品"));
        fixture.givenNoPriorActivity()
                .when(cmd)
                .expectException(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("US04: 数量が 0 以下の場合は例外")
    void 数量がゼロ以下の場合は例外() {
        BookCargoCommand cmd = new BookCargoCommand(
                "B-006",
                "S-001",
                new RouteSpecification("JPTYO", "USNYC", LocalDate.of(2026, 9, 30)),
                new CargoSpecification(
                        CargoType.GENERAL,
                        new BigDecimal("1500.00"),
                        new Dimensions(120, 80, 60),
                        0,
                        "電子部品"));
        fixture.givenNoPriorActivity()
                .when(cmd)
                .expectException(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("US05: 危険物貨物の予約を登録できる")
    void 危険物貨物の予約を登録できる() {
        BookCargoCommand cmd = new BookCargoCommand(
                "B-101",
                "S-001",
                new RouteSpecification("JPTYO", "USNYC", LocalDate.of(2026, 9, 30)),
                new CargoSpecification(
                        CargoType.HAZARDOUS,
                        new BigDecimal("1500.00"),
                        new Dimensions(120, 80, 60),
                        10,
                        "アセトン",
                        new HazardInfo("3", "UN1090", "引火性液体・直射日光厳禁"),
                        null));
        fixture.givenNoPriorActivity()
                .when(cmd)
                .expectSuccessfulHandlerExecution()
                .expectEvents(new CargoBookedEvent(
                        "B-101",
                        "S-001",
                        cmd.routeSpec(),
                        cmd.cargoSpec(),
                        "PRELIMINARY",
                        "NOT_ROUTED"));
    }

    @Test
    @DisplayName("US05: 危険物貨物で hazardInfo が null の場合は例外")
    void 危険物貨物でhazardInfoがnullの場合は例外() {
        BookCargoCommand cmd = new BookCargoCommand(
                "B-102",
                "S-001",
                new RouteSpecification("JPTYO", "USNYC", LocalDate.of(2026, 9, 30)),
                new CargoSpecification(
                        CargoType.HAZARDOUS,
                        new BigDecimal("1500.00"),
                        new Dimensions(120, 80, 60),
                        10,
                        "アセトン",
                        null,
                        null));
        fixture.givenNoPriorActivity()
                .when(cmd)
                .expectException(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("US05: 危険物貨物で UN 番号が空文字列の場合は例外")
    void 危険物貨物でUN番号が空文字列の場合は例外() {
        BookCargoCommand cmd = new BookCargoCommand(
                "B-103",
                "S-001",
                new RouteSpecification("JPTYO", "USNYC", LocalDate.of(2026, 9, 30)),
                new CargoSpecification(
                        CargoType.HAZARDOUS,
                        new BigDecimal("1500.00"),
                        new Dimensions(120, 80, 60),
                        10,
                        "アセトン",
                        new HazardInfo("3", "", "引火性液体"),
                        null));
        fixture.givenNoPriorActivity()
                .when(cmd)
                .expectException(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("US05: 冷凍貨物の予約を登録できる")
    void 冷凍貨物の予約を登録できる() {
        BookCargoCommand cmd = new BookCargoCommand(
                "B-104",
                "S-001",
                new RouteSpecification("JPTYO", "USNYC", LocalDate.of(2026, 9, 30)),
                new CargoSpecification(
                        CargoType.REFRIGERATED,
                        new BigDecimal("2000.00"),
                        new Dimensions(150, 100, 80),
                        20,
                        "冷凍マグロ",
                        null,
                        new TemperatureCondition(new BigDecimal("-25.0"), new BigDecimal("-18.0"))));
        fixture.givenNoPriorActivity()
                .when(cmd)
                .expectSuccessfulHandlerExecution()
                .expectEvents(new CargoBookedEvent(
                        "B-104",
                        "S-001",
                        cmd.routeSpec(),
                        cmd.cargoSpec(),
                        "PRELIMINARY",
                        "NOT_ROUTED"));
    }

    @Test
    @DisplayName("US05: 冷凍貨物で temperatureCondition が null の場合は例外")
    void 冷凍貨物でtemperatureConditionがnullの場合は例外() {
        BookCargoCommand cmd = new BookCargoCommand(
                "B-105",
                "S-001",
                new RouteSpecification("JPTYO", "USNYC", LocalDate.of(2026, 9, 30)),
                new CargoSpecification(
                        CargoType.REFRIGERATED,
                        new BigDecimal("2000.00"),
                        new Dimensions(150, 100, 80),
                        20,
                        "冷凍マグロ",
                        null,
                        null));
        fixture.givenNoPriorActivity()
                .when(cmd)
                .expectException(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("US05: 冷凍貨物で min > max の場合は例外")
    void 冷凍貨物でminがmaxより大きい場合は例外() {
        BookCargoCommand cmd = new BookCargoCommand(
                "B-106",
                "S-001",
                new RouteSpecification("JPTYO", "USNYC", LocalDate.of(2026, 9, 30)),
                new CargoSpecification(
                        CargoType.REFRIGERATED,
                        new BigDecimal("2000.00"),
                        new Dimensions(150, 100, 80),
                        20,
                        "冷凍マグロ",
                        null,
                        new TemperatureCondition(new BigDecimal("-10.0"), new BigDecimal("-25.0"))));
        fixture.givenNoPriorActivity()
                .when(cmd)
                .expectException(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("US05: 一般貨物で hazardInfo が指定された場合は例外")
    void 一般貨物でhazardInfoが指定された場合は例外() {
        BookCargoCommand cmd = new BookCargoCommand(
                "B-107",
                "S-001",
                new RouteSpecification("JPTYO", "USNYC", LocalDate.of(2026, 9, 30)),
                new CargoSpecification(
                        CargoType.GENERAL,
                        new BigDecimal("1500.00"),
                        new Dimensions(120, 80, 60),
                        10,
                        "電子部品",
                        new HazardInfo("3", "UN1090", "誤指定"),
                        null));
        fixture.givenNoPriorActivity()
                .when(cmd)
                .expectException(IllegalArgumentException.class);
    }

    private CargoBookedEvent bookedEvent(String bookingId) {
        BookCargoCommand cmd = validGeneralCommand(bookingId);
        return new CargoBookedEvent(
                bookingId, cmd.shipperId(), cmd.routeSpec(), cmd.cargoSpec(),
                "PRELIMINARY", "NOT_ROUTED");
    }

    @Test
    @DisplayName("US06: 仮受付の予約を経路設計に引き渡せる")
    void 仮受付の予約を経路設計に引き渡せる() {
        fixture.given(bookedEvent("B-201"))
                .when(new RequestRouteDesignCommand("B-201"))
                .expectSuccessfulHandlerExecution()
                .expectEvents(new RouteDesignRequestedEvent(
                        "B-201", "ROUTING", "JPTYO", "USNYC", LocalDate.of(2026, 9, 30), "GENERAL"));
    }

    @Test
    @DisplayName("US06: 既に経路設計中の予約は再度引き渡せない")
    void 既に経路設計中の予約は再度引き渡せない() {
        fixture.given(bookedEvent("B-202"),
                        new RouteDesignRequestedEvent("B-202", "ROUTING", "JPTYO", "USNYC", LocalDate.of(2026, 9, 30), "GENERAL"))
                .when(new RequestRouteDesignCommand("B-202"))
                .expectException(IllegalStateException.class);
    }

    @Test
    @DisplayName("US13: 仮受付の予約をキャンセルできる")
    void 仮受付の予約をキャンセルできる() {
        fixture.given(bookedEvent("B-301"))
                .when(new CancelBookingCommand("B-301"))
                .expectEvents(new BookingCancelledEvent("B-301", "CANCELLED"));
    }

    @Test
    @DisplayName("US13: 経路設計中の予約をキャンセルできる")
    void 経路設計中の予約をキャンセルできる() {
        fixture.given(bookedEvent("B-302"),
                        new RouteDesignRequestedEvent("B-302", "ROUTING", "JPTYO", "USNYC", LocalDate.of(2026, 9, 30), "GENERAL"))
                .when(new CancelBookingCommand("B-302"))
                .expectEvents(new BookingCancelledEvent("B-302", "CANCELLED"));
    }

    @Test
    @DisplayName("US13: 経路提案中でない予約は確定できない（ROUTE_PROPOSED 到達は IT4）")
    void 経路提案中でない予約は確定できない() {
        fixture.given(bookedEvent("B-303"))
                .when(new ConfirmBookingCommand("B-303"))
                .expectException(IllegalStateException.class);
    }

    @Test
    @DisplayName("US13: 確定済みの予約はキャンセルできない")
    void 確定済みの予約はキャンセルできない() {
        fixture.given(bookedEvent("B-304"), new BookingConfirmedEvent("B-304", "CONFIRMED"))
                .when(new CancelBookingCommand("B-304"))
                .expectException(IllegalStateException.class);
    }

    @Test
    @DisplayName("US14 / IT5 1.4: 予約確定（CONFIRMED）に追跡情報を割当できる")
    void 予約確定後に追跡情報を割当できる() {
        fixture.given(bookedEvent("B-501"), new BookingConfirmedEvent("B-501", "CONFIRMED"))
                .when(new AssignTrackingDetailsCommand("B-501", "TRK-AB12CD3456"))
                .expectSuccessfulHandlerExecution()
                .expectEvents(new CargoTrackingAssignedEvent(
                        "B-501", "TRK-AB12CD3456", "TRACKING_ISSUED"));
    }

    @Test
    @DisplayName("US14 / IT5 1.4: 予約確定前は追跡情報を割当できない（仮受付状態）")
    void 予約確定前は追跡情報を割当できない() {
        fixture.given(bookedEvent("B-502"))
                .when(new AssignTrackingDetailsCommand("B-502", "TRK-AB12CD3456"))
                .expectException(IllegalStateException.class);
    }

    @Test
    @DisplayName("US14 / IT5 1.4: 追跡情報割当後の二重割当は拒否（TRACKING_ISSUED 以降）")
    void 追跡情報割当後の二重割当は拒否() {
        fixture.given(
                        bookedEvent("B-503"),
                        new BookingConfirmedEvent("B-503", "CONFIRMED"),
                        new CargoTrackingAssignedEvent("B-503", "TRK-EXISTING01", "TRACKING_ISSUED"))
                .when(new AssignTrackingDetailsCommand("B-503", "TRK-ANOTHER999"))
                .expectException(IllegalStateException.class);
    }

    private RouteDesignRequestedEvent routingEvent(String bookingId) {
        return new RouteDesignRequestedEvent(
                bookingId, "ROUTING", "JPTYO", "USNYC", LocalDate.of(2026, 9, 30), "GENERAL");
    }

    private List<Leg> validItinerary() {
        return List.of(new Leg(
                "V-100", "JPTYO", "USNYC",
                LocalDateTime.of(2026, 7, 3, 9, 0), LocalDateTime.of(2026, 7, 28, 18, 0)));
    }

    @Test
    @DisplayName("US11: 経路設計中の予約に経路を割り当てると経路提案中へ遷移する")
    void 経路設計中の予約に経路を割り当てると経路提案中へ遷移する() {
        List<Leg> legs = validItinerary();
        fixture.given(bookedEvent("B-401"), routingEvent("B-401"))
                .when(new AssignRouteToCargoCommand("B-401", legs))
                .expectSuccessfulHandlerExecution()
                .expectEvents(new CargoRoutedEvent("B-401", "ROUTE_PROPOSED", "ROUTED", legs));
    }

    @Test
    @DisplayName("US11: 仮受付の予約には経路を割り当てられない")
    void 仮受付の予約には経路を割り当てられない() {
        fixture.given(bookedEvent("B-402"))
                .when(new AssignRouteToCargoCommand("B-402", validItinerary()))
                .expectException(IllegalStateException.class);
    }

    @Test
    @DisplayName("US11: 経路が空の場合は割り当てられない")
    void 経路が空の場合は割り当てられない() {
        fixture.given(bookedEvent("B-403"), routingEvent("B-403"))
                .when(new AssignRouteToCargoCommand("B-403", List.of()))
                .expectException(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("US11: 出発地が予約と一致しない経路は割り当てられない")
    void 出発地が予約と一致しない経路は割り当てられない() {
        List<Leg> legs = List.of(new Leg(
                "V-100", "JPOSA", "USNYC",
                LocalDateTime.of(2026, 7, 3, 9, 0), LocalDateTime.of(2026, 7, 28, 18, 0)));
        fixture.given(bookedEvent("B-404"), routingEvent("B-404"))
                .when(new AssignRouteToCargoCommand("B-404", legs))
                .expectException(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("US11: 最終到着地が目的地と一致しない経路は割り当てられない")
    void 最終到着地が目的地と一致しない経路は割り当てられない() {
        List<Leg> legs = List.of(new Leg(
                "V-100", "JPTYO", "SGSIN",
                LocalDateTime.of(2026, 7, 3, 9, 0), LocalDateTime.of(2026, 7, 28, 18, 0)));
        fixture.given(bookedEvent("B-405"), routingEvent("B-405"))
                .when(new AssignRouteToCargoCommand("B-405", legs))
                .expectException(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("US11: 最終到着日が到着期限を超える経路は割り当てられない")
    void 最終到着日が到着期限を超える経路は割り当てられない() {
        List<Leg> legs = List.of(new Leg(
                "V-100", "JPTYO", "USNYC",
                LocalDateTime.of(2026, 9, 1, 9, 0), LocalDateTime.of(2026, 10, 15, 18, 0)));
        fixture.given(bookedEvent("B-406"), routingEvent("B-406"))
                .when(new AssignRouteToCargoCommand("B-406", legs))
                .expectException(IllegalArgumentException.class);
    }

    private CargoRoutedEvent routedEvent(String bookingId) {
        return new CargoRoutedEvent(bookingId, "ROUTE_PROPOSED", "ROUTED", validItinerary());
    }

    @Test
    @DisplayName("US12: 経路提案中の予約は確定経路を荷主に通知できる")
    void 経路提案中の予約は確定経路を荷主に通知できる() {
        fixture.given(bookedEvent("B-501"), routingEvent("B-501"), routedEvent("B-501"))
                .when(new NotifyRouteToShipperCommand("B-501"))
                .expectSuccessfulHandlerExecution()
                .expectEvents(new RouteNotifiedToShipperEvent("B-501"));
    }

    @Test
    @DisplayName("US12: 経路提案中でない予約は荷主に通知できない")
    void 経路提案中でない予約は荷主に通知できない() {
        fixture.given(bookedEvent("B-502"), routingEvent("B-502"))
                .when(new NotifyRouteToShipperCommand("B-502"))
                .expectException(IllegalStateException.class);
    }

    // --- IT7 Task 4.5：US23 MarkBookingSettledCommand（cross-service SETTLED 反映） ---

    @Test
    @DisplayName("US23 T4.5: TRACKING_ISSUED 状態で MarkBookingSettledCommand → BookingSettledEvent")
    void US23_精算済遷移() {
        fixture.given(
                        bookedEvent("B-S01"),
                        new BookingConfirmedEvent("B-S01", "CONFIRMED"),
                        new CargoTrackingAssignedEvent("B-S01", "TRK-AB12CD3456", "TRACKING_ISSUED"))
                .when(new MarkBookingSettledCommand("B-S01"))
                .expectSuccessfulHandlerExecution()
                .expectEvents(new BookingSettledEvent("B-S01", "SETTLED"));
    }

    @Test
    @DisplayName("US23 T4.5: CONFIRMED 状態でも MarkBookingSettledCommand 受理可能")
    void US23_CONFIRMEDからでも精算済遷移可() {
        fixture.given(
                        bookedEvent("B-S02"),
                        new BookingConfirmedEvent("B-S02", "CONFIRMED"))
                .when(new MarkBookingSettledCommand("B-S02"))
                .expectSuccessfulHandlerExecution()
                .expectEvents(new BookingSettledEvent("B-S02", "SETTLED"));
    }

    @Test
    @DisplayName("US23 T4.5: 既に SETTLED の場合は冪等スキップ（重複配信対策）")
    void US23_SETTLED冪等スキップ() {
        fixture.given(
                        bookedEvent("B-S03"),
                        new BookingConfirmedEvent("B-S03", "CONFIRMED"),
                        new BookingSettledEvent("B-S03", "SETTLED"))
                .when(new MarkBookingSettledCommand("B-S03"))
                .expectSuccessfulHandlerExecution()
                .expectNoEvents();
    }

    @Test
    @DisplayName("US23 T4.5: PRELIMINARY（CONFIRMED 前）は SETTLED へ遷移できない")
    void US23_PRELIMINARYからは精算済遷移不可() {
        fixture.given(bookedEvent("B-S04"))
                .when(new MarkBookingSettledCommand("B-S04"))
                .expectException(IllegalStateException.class);
    }

    @Test
    @DisplayName("US23 T4.5: CANCELLED 状態は冪等スキップ（精算しない）")
    void US23_CANCELLED冪等スキップ() {
        fixture.given(
                        bookedEvent("B-S05"),
                        new BookingCancelledEvent("B-S05", "CANCELLED"))
                .when(new MarkBookingSettledCommand("B-S05"))
                .expectSuccessfulHandlerExecution()
                .expectNoEvents();
    }
}
