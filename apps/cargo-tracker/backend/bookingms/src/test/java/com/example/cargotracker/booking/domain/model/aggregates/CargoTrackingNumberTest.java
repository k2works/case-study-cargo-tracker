package com.example.cargotracker.booking.domain.model.aggregates;

import com.example.cargotracker.booking.domain.model.commands.IssueTrackingNumberCommand;
import com.example.cargotracker.booking.domain.model.commands.RevertTrackingNumberCommand;
import com.example.cargotracker.booking.domain.model.events.BookingConfirmedEvent;
import com.example.cargotracker.booking.domain.model.events.CargoBookedEvent;
import com.example.cargotracker.booking.domain.model.events.CargoRoutedEvent;
import com.example.cargotracker.booking.domain.model.events.RoutingRequestedEvent;
import com.example.cargotracker.booking.domain.model.events.ShipperNotifiedEvent;
import com.example.cargotracker.booking.domain.model.events.TrackingNumberIssuedEvent;
import com.example.cargotracker.booking.domain.model.events.TrackingNumberRevertedEvent;
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
 * 追跡番号の発行（UC12 / US14）。
 *
 * <p>中核の判断は<b>「二重に発行しない」</b>（不変条件 8）と<b>「確定した予約だけ」</b>。
 * 発行から連鎖が始まるので、2 度発行すると追跡が 2 つ作られる。</p>
 */
class CargoTrackingNumberTest {

    private static final ZoneId ZONE = ZoneId.of("Asia/Tokyo");
    private static final Instant NOW = Instant.parse("2026-09-06T00:00:00Z");
    private static final LocalDate DEADLINE = LocalDate.of(2026, Month.DECEMBER, 1);
    private static final Instant LOAD = Instant.parse("2026-09-10T00:00:00Z");
    private static final Instant UNLOAD = Instant.parse("2026-09-24T09:00:00Z");

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
                List.of(new CargoRoutedEvent.Leg("V-MOL-001", "JPTYO", "SGSIN", LOAD,
                                Instant.parse("2026-09-15T00:00:00Z")),
                        new CargoRoutedEvent.Leg("V-MSK-220", "SGSIN", "USNYC",
                                Instant.parse("2026-09-16T00:00:00Z"), UNLOAD)),
                "routing01", NOW);
    }

    /** 確定済みの予約のイベント列。ここまで来て初めて追跡番号を発行できる。 */
    private static Object[] confirmed() {
        return new Object[] {
            booked(), new RoutingRequestedEvent("B-0001", "sales01"), routed(),
            new ShipperNotifiedEvent("B-0001", "shipper@example.com", "案内", "sales01", NOW),
            new BookingConfirmedEvent("B-0001", "sales01", NOW),
        };
    }

    @Test
    @DisplayName("US14 §1: 確定した予約に追跡番号を発行できる（旅程も載る）")
    void issuesTrackingNumber() {
        // **legs を落とさない。** 購読側（handlingms の CargoSnapshot・IT9）がまだ
        // 無くても載せる。契約イベントは追記専用で、あとから形を変えられない。
        fixture.given().events(confirmed())
                .when().command(new IssueTrackingNumberCommand("B-0001", "T-0001", "routing01"))
                .then().success()
                .events(new TrackingNumberIssuedEvent("B-0001", "T-0001", "JPTYO", "USNYC",
                        "GENERAL",
                        List.of(new TrackingNumberIssuedEvent.Leg("V-MOL-001", "JPTYO", "SGSIN",
                                        LOAD, Instant.parse("2026-09-15T00:00:00Z")),
                                new TrackingNumberIssuedEvent.Leg("V-MSK-220", "SGSIN", "USNYC",
                                        Instant.parse("2026-09-16T00:00:00Z"), UNLOAD)),
                        "routing01", NOW));
    }

    @Test
    @DisplayName("US14: 不変条件 8 — 二重に発行しない")
    void rejectsSecondIssue() {
        // 発行から連鎖が始まる。2 度発行すると追跡が 2 つ作られる。
        fixture.given().events(booked(), new RoutingRequestedEvent("B-0001", "sales01"),
                        routed(),
                        new ShipperNotifiedEvent("B-0001", "s@example.com", "案内", "sales01", NOW),
                        new BookingConfirmedEvent("B-0001", "sales01", NOW),
                        new TrackingNumberIssuedEvent("B-0001", "T-0001", "JPTYO", "USNYC",
                                "GENERAL", List.of(), "routing01", NOW))
                .when().command(new IssueTrackingNumberCommand("B-0001", "T-0002", "routing02"))
                .then().exception(IllegalTransition.class);
    }

    @Test
    @DisplayName("US14 §1: 確定していない予約には発行できない")
    void rejectsUnconfirmedBooking() {
        fixture.given().events(booked(), new RoutingRequestedEvent("B-0001", "sales01"),
                        routed(),
                        new ShipperNotifiedEvent("B-0001", "s@example.com", "案内", "sales01", NOW))
                .when().command(new IssueTrackingNumberCommand("B-0001", "T-0001", "routing01"))
                .then().exception(IllegalTransition.class);
    }

    @Test
    @DisplayName("US14: 番号の無い発行は断る（採番は投影が行う）")
    void rejectsBlankTrackingNumber() {
        fixture.given().events(confirmed())
                .when().command(new IssueTrackingNumberCommand("B-0001", "  ", "routing01"))
                .then().exception(com.example.cargotracker.shared.domain.error
                        .BusinessRuleViolation.class);
    }

    @Test
    @DisplayName("受け付けていない予約には発行できない")
    void rejectsUnknownBooking() {
        fixture.given().noPriorActivity()
                .when().command(new IssueTrackingNumberCommand("B-NONE", "T-0001", "routing01"))
                .then().exception(IllegalTransition.class);
    }

    @Test
    @DisplayName("ADR-0010 決定 4: 発行済みの追跡番号を取り消せる（予約は確定に戻る）")
    void revertsTrackingNumber() {
        // 補償。**キャンセルではない**ので、経路設計者がもう一度発行できる状態にする。
        fixture.given().events(booked(), new RoutingRequestedEvent("B-0001", "sales01"),
                        routed(),
                        new ShipperNotifiedEvent("B-0001", "s@example.com", "案内", "sales01", NOW),
                        new BookingConfirmedEvent("B-0001", "sales01", NOW),
                        new TrackingNumberIssuedEvent("B-0001", "T-0001", "JPTYO", "USNYC",
                                "GENERAL", List.of(), "routing01", NOW))
                .when().command(new RevertTrackingNumberCommand("B-0001", "届きませんでした"))
                .then().success()
                .events(new TrackingNumberRevertedEvent("B-0001", "T-0001",
                        "届きませんでした", NOW));
    }

    @Test
    @DisplayName("ADR-0010 決定 4: 取り消したあとはもう一度発行できる")
    void allowsReissueAfterRevert() {
        fixture.given().events(booked(), new RoutingRequestedEvent("B-0001", "sales01"),
                        routed(),
                        new ShipperNotifiedEvent("B-0001", "s@example.com", "案内", "sales01", NOW),
                        new BookingConfirmedEvent("B-0001", "sales01", NOW),
                        new TrackingNumberIssuedEvent("B-0001", "T-0001", "JPTYO", "USNYC",
                                "GENERAL", List.of(), "routing01", NOW),
                        new TrackingNumberRevertedEvent("B-0001", "T-0001", "届かず", NOW))
                .when().command(new IssueTrackingNumberCommand("B-0001", "T-0002", "routing01"))
                .then().success();
    }

    @Test
    @DisplayName("発行していない予約の追跡番号は取り消せない（再試行で 2 度届いても 1 度だけ効く）")
    void rejectsRevertWhenNotIssued() {
        fixture.given().events(confirmed())
                .when().command(new RevertTrackingNumberCommand("B-0001", "届かず"))
                .then().exception(IllegalTransition.class);
    }

    @Test
    @DisplayName("受け付けていない予約の追跡番号は取り消せない（500 にしない）")
    void rejectsRevertForUnknownBooking() {
        fixture.given().noPriorActivity()
                .when().command(new RevertTrackingNumberCommand("B-NONE", "届かず"))
                .then().exception(IllegalTransition.class);
    }
}
