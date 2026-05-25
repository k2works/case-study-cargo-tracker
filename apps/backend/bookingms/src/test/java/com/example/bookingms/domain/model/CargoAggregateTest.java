package com.example.bookingms.domain.model;

import com.example.bookingms.domain.commands.BookCargoCommand;
import com.example.bookingms.domain.events.CargoBookedEvent;
import org.axonframework.test.aggregate.AggregateTestFixture;
import org.axonframework.test.aggregate.FixtureConfiguration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;

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
}
