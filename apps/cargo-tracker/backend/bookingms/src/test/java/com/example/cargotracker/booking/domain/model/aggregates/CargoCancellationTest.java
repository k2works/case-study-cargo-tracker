package com.example.cargotracker.booking.domain.model.aggregates;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.cargotracker.booking.domain.model.commands.ApproveCancellationCommand;
import com.example.cargotracker.booking.domain.model.commands.RejectCancellationCommand;
import com.example.cargotracker.booking.domain.model.commands.RequestCancellationCommand;
import com.example.cargotracker.booking.domain.model.events.BookingDeliveredEvent;
import com.example.cargotracker.booking.domain.model.events.CancellationApprovedEvent;
import com.example.cargotracker.booking.domain.model.events.CancellationRejectedEvent;
import com.example.cargotracker.booking.domain.model.events.CancellationRequestedEvent;
import com.example.cargotracker.booking.domain.model.events.CargoBookedEvent;
import com.example.cargotracker.booking.domain.model.events.CargoRoutedEvent;
import com.example.cargotracker.booking.domain.model.events.HandlingRecordedEvent;
import com.example.cargotracker.booking.domain.model.events.TrackingNumberIssuedEvent;
import com.example.cargotracker.shared.contract.event.CargoCancelledEvent;
import com.example.cargotracker.shared.domain.error.BusinessRuleViolation;
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
 * 輸送中キャンセルの承認（US30 / UC22 / 不変条件 9・9-2・10）。
 *
 * <p><b>入口は 1 つ。</b> 輸送開始前は即座にキャンセルになり、輸送中は申請になる。
 * どちらになるかを決めるのは集約で、画面はその述語を呼ぶだけである——出し分けを
 * 画面に書くと、同じ判断が 2 か所に住む。</p>
 */
class CargoCancellationTest {

    private static final String BOOKING = "B-0001";
    private static final LocalDate DEADLINE = LocalDate.of(2026, Month.DECEMBER, 1);
    private static final Instant NOW = Instant.parse("2026-09-25T02:00:00Z");
    private static final String REQUEST = "CR-0001";

    private AxonTestFixture fixture;

    @BeforeEach
    void setUp() {
        EventSourcingConfigurer configurer = EventSourcingConfigurer.create()
                .registerEntity(EventSourcedEntityModule.autodetected(String.class, Cargo.class))
                .componentRegistry(registry -> registry.registerComponent(
                        Clock.class, c -> Clock.fixed(NOW, ZoneId.of("Asia/Tokyo"))));
        fixture = AxonTestFixture.with(configurer, c -> c.disableAxonServer());
    }

    private static CargoBookedEvent booked() {
        return new CargoBookedEvent(BOOKING, "SHP-000001", "JPTYO", "USNYC", DEADLINE,
                "GENERAL", new BigDecimal("1200"), new BigDecimal("120"),
                new BigDecimal("80"), new BigDecimal("100"), 10, "止める貨物",
                null, null, null, null, "sales01");
    }

    /** 東京 → シンガポール → ニューヨークの旅程。寄港地が 1 つある形で見る。 */
    private static CargoRoutedEvent routed() {
        return new CargoRoutedEvent(BOOKING, List.of(
                new CargoRoutedEvent.Leg("V-MOL-001", "JPTYO", "SGSIN",
                        Instant.parse("2026-09-20T00:00:00Z"),
                        Instant.parse("2026-10-02T00:00:00Z")),
                new CargoRoutedEvent.Leg("V-MSK-220", "SGSIN", "USNYC",
                        Instant.parse("2026-10-03T00:00:00Z"),
                        Instant.parse("2026-10-12T00:00:00Z"))),
                "routing01", Instant.parse("2026-09-06T00:00:00Z"));
    }

    private static TrackingNumberIssuedEvent issued() {
        return new TrackingNumberIssuedEvent(BOOKING, "TRK-8K2QX7M4RB", "SHP-000001",
                "JPTYO", "USNYC", "GENERAL", new BigDecimal("1200"), List.of(),
                "routing01", Instant.parse("2026-09-10T00:00:00Z"));
    }

    /** 東京で受領＝輸送中。現在地は東京。 */
    private static HandlingRecordedEvent receivedAtTokyo() {
        return new HandlingRecordedEvent(BOOKING, "act-1", "RECEIVE", "JPTYO",
                Instant.parse("2026-09-20T01:00:00Z"), Instant.parse("2026-09-20T01:05:00Z"));
    }

    private static RequestCancellationCommand request(String reason) {
        return new RequestCancellationCommand(BOOKING, REQUEST, reason, "sales01");
    }

    @Test
    @DisplayName("US30 §1: 輸送開始前はその場でキャンセルになる（申請を挟まない）")
    void cancelsImmediatelyBeforeDeparture() {
        fixture.given().event(booked()).event(routed()).event(issued())
                .when().command(request("荷主の発注取消"))
                .then().events(new CargoCancelledEvent(BOOKING, "TRK-8K2QX7M4RB",
                        "TRACKING_ISSUED", null, "荷主の発注取消", "sales01", NOW));
    }

    @Test
    @DisplayName("US30 §2: 輸送中は申請になり、予約の状態は動かない")
    void requestsApprovalWhileInTransit() {
        fixture.given().event(booked()).event(routed()).event(issued())
                .event(receivedAtTokyo())
                .when().command(request("荷主の発注取消"))
                .then().events(new CancellationRequestedEvent(BOOKING, REQUEST,
                        "荷主の発注取消", "sales01", NOW));
    }

    @Test
    @DisplayName("US30 §3: 理由の無い申請は断る")
    void requiresAReason() {
        fixture.given().event(booked())
                .when().command(request("  "))
                .then().exception(BusinessRuleViolation.class);
    }

    @Test
    @DisplayName("US30 §8: 配送完了以降はキャンセルできない")
    void refusesAfterDelivery() {
        fixture.given().event(booked()).event(routed()).event(issued())
                .event(receivedAtTokyo())
                .event(new BookingDeliveredEvent(BOOKING, "TRK-8K2QX7M4RB",
                        Instant.parse("2026-10-12T02:00:00Z"), "USNYC"))
                .when().command(request("荷主の発注取消"))
                .then().exception(IllegalTransition.class);
    }

    @Test
    @DisplayName("不変条件 10: 未決着の申請があるあいだは、二度目を受け付けない")
    void refusesASecondPendingRequest() {
        fixture.given().event(booked()).event(routed()).event(issued())
                .event(receivedAtTokyo())
                .event(new CancellationRequestedEvent(BOOKING, REQUEST, "荷主の発注取消",
                        "sales01", NOW))
                .when().command(new RequestCancellationCommand(BOOKING, "CR-0002",
                        "やはり止めたい", "sales01"))
                .then().exception(IllegalTransition.class);
    }

    @Test
    @DisplayName("US30 §5・§6: 残りの寄港地を指定して承認すると、判断とキャンセルが揃って出る")
    void approvesWithADischargePort() {
        fixture.given().event(booked()).event(routed()).event(issued())
                .event(receivedAtTokyo())
                .event(new CancellationRequestedEvent(BOOKING, REQUEST, "荷主の発注取消",
                        "sales01", NOW))
                .when().command(new ApproveCancellationCommand(BOOKING, "SGSIN",
                        "荷主の指定倉庫が近い", "tracker01"))
                // **判断とキャンセルは対で出す。** 予約がキャンセルになったことは
                // 契約イベントが伝えるが、誰が・どこで降ろすと決めたかは履歴が読む。
                .then().events(
                        new CancellationApprovedEvent(BOOKING, REQUEST, "SGSIN",
                                "荷主の指定倉庫が近い", "tracker01", NOW),
                        new CargoCancelledEvent(BOOKING, "TRK-8K2QX7M4RB", "IN_TRANSIT",
                                "SGSIN", "荷主の発注取消", "tracker01", NOW));
    }

    @Test
    @DisplayName("不変条件 9-2: 現在地でも承認できる（いま居る港で降ろす）")
    void approvesAtTheCurrentPort() {
        fixture.given().event(booked()).event(routed()).event(issued())
                .event(receivedAtTokyo())
                .event(new CancellationRequestedEvent(BOOKING, REQUEST, "荷主の発注取消",
                        "sales01", NOW))
                .when().command(new ApproveCancellationCommand(BOOKING, "JPTYO", null,
                        "tracker01"))
                .then().success();
    }

    @Test
    @DisplayName("不変条件 9-2: いま居る港から先の寄港地はすべて候補になる")
    void keepsPortsAheadAsCandidates() {
        // **積み港に居ることは「その区間を通った」ことではない。** 東京で受領した
        // 貨物にとって、東京 → シンガポールはまだ先である。積み港も通過済みと
        // 数えると、**次の寄港地が候補から消える**（実装して最初に踏んだ欠陥）。
        fixture.given().event(booked()).event(routed()).event(issued())
                .event(receivedAtTokyo())
                .event(new CancellationRequestedEvent(BOOKING, REQUEST, "荷主の発注取消",
                        "sales01", NOW))
                .when().command(new ApproveCancellationCommand(BOOKING, "SGSIN", null,
                        "tracker01"))
                .then().success();
    }

    @Test
    @DisplayName("不変条件 9-2: 荷降しの済んだ港は候補から外れる（船はもう戻らない）")
    void dropsPortsAlreadyUnloaded() {
        fixture.given().event(booked()).event(routed()).event(issued())
                .event(receivedAtTokyo())
                .event(new HandlingRecordedEvent(BOOKING, "act-2", "UNLOAD", "SGSIN",
                        Instant.parse("2026-10-02T01:00:00Z"),
                        Instant.parse("2026-10-02T01:05:00Z")))
                .event(new CancellationRequestedEvent(BOOKING, REQUEST, "荷主の発注取消",
                        "sales01", NOW))
                .when().command(new ApproveCancellationCommand(BOOKING, "JPTYO", null,
                        "tracker01"))
                .then().exception(BusinessRuleViolation.class);
    }

    @Test
    @DisplayName("不変条件 9-2: 旅程に無い港は断る")
    void refusesAPortOutsideTheItinerary() {
        fixture.given().event(booked()).event(routed()).event(issued())
                .event(receivedAtTokyo())
                .event(new CancellationRequestedEvent(BOOKING, REQUEST, "荷主の発注取消",
                        "sales01", NOW))
                .when().command(new ApproveCancellationCommand(BOOKING, "GBLON", null,
                        "tracker01"))
                .then().exception(BusinessRuleViolation.class);
    }

    @Test
    @DisplayName("申請の無い予約は承認できない（判断する相手がいない）")
    void refusesApprovalWithoutARequest() {
        fixture.given().event(booked()).event(routed()).event(issued())
                .event(receivedAtTokyo())
                .when().command(new ApproveCancellationCommand(BOOKING, "SGSIN", null,
                        "tracker01"))
                .then().exception(IllegalTransition.class);
    }

    @Test
    @DisplayName("US30 §7: 却下すると予約は輸送中のまま、理由が残る")
    void rejectsAndKeepsTheBookingInTransit() {
        fixture.given().event(booked()).event(routed()).event(issued())
                .event(receivedAtTokyo())
                .event(new CancellationRequestedEvent(BOOKING, REQUEST, "荷主の発注取消",
                        "sales01", NOW))
                .when().command(new RejectCancellationCommand(BOOKING,
                        "荷受人がすでに手配済み", "tracker01"))
                .then().events(new CancellationRejectedEvent(BOOKING, REQUEST,
                        "荷受人がすでに手配済み", "tracker01", NOW));
    }

    @Test
    @DisplayName("却下したあとは、もう一度申請できる（状況が変われば止めたくなる）")
    void allowsANewRequestAfterRejection() {
        fixture.given().event(booked()).event(routed()).event(issued())
                .event(receivedAtTokyo())
                .event(new CancellationRequestedEvent(BOOKING, REQUEST, "荷主の発注取消",
                        "sales01", NOW))
                .event(new CancellationRejectedEvent(BOOKING, REQUEST, "手配済み",
                        "tracker01", NOW))
                .when().command(new RequestCancellationCommand(BOOKING, "CR-0002",
                        "やはり止めたい", "sales01"))
                .then().success();
    }

    @Test
    @DisplayName("キャンセル済の予約は二度キャンセルしない（二度届いても 1 度だけ）")
    void isIdempotentOnceCancelled() {
        fixture.given().event(booked()).event(routed()).event(issued())
                .event(new CargoCancelledEvent(BOOKING, "TRK-8K2QX7M4RB",
                        "TRACKING_ISSUED", null, "荷主の発注取消", "sales01", NOW))
                .when().command(request("やはり止めたい"))
                .then().exception(IllegalTransition.class);
    }

    @Test
    @DisplayName("却下には理由が要る（申請した営業が次の手を決められない）")
    void requiresAReasonToReject() {
        fixture.given().event(booked()).event(routed()).event(issued())
                .event(receivedAtTokyo())
                .event(new CancellationRequestedEvent(BOOKING, REQUEST, "荷主の発注取消",
                        "sales01", NOW))
                .when().command(new RejectCancellationCommand(BOOKING, "  ", "tracker01"))
                .then().exception(BusinessRuleViolation.class);
    }

    @Test
    @DisplayName("知らない予約には申請も判断もできない（API を直接叩いても守る）")
    void refusesForAnUnknownBooking() {
        fixture.given().noPriorActivity()
                .when().command(request("荷主の発注取消"))
                .then().exception(IllegalTransition.class);

        fixture.given().noPriorActivity()
                .when().command(new ApproveCancellationCommand(BOOKING, "SGSIN", null,
                        "tracker01"))
                .then().exception(IllegalTransition.class);

        fixture.given().noPriorActivity()
                .when().command(new RejectCancellationCommand(BOOKING, "手配済み",
                        "tracker01"))
                .then().exception(IllegalTransition.class);
    }

    @Test
    @DisplayName("荷役がまだ無いなら、候補は旅程の荷降し港だけ（現在地は無い）")
    void offersItineraryPortsWhenNothingHasBeenHandled() {
        // 本番の経路では輸送中になる前に受領があるので起きないが、**現在地が
        // 無いときに候補が空にならない**ことを固定しておく——空になると、
        // 承認そのものができなくなる。
        fixture.given().event(booked()).event(routed()).event(issued())
                .event(new CancellationRequestedEvent(BOOKING, REQUEST, "荷主の発注取消",
                        "sales01", NOW))
                .when().command(new ApproveCancellationCommand(BOOKING, "USNYC", null,
                        "tracker01"))
                .then().success();
    }

    @Test
    @DisplayName("キャンセル済でも予約の状態は読める（終端はキャンセル）")
    void endsAtCancelled() {
        assertThat(com.example.cargotracker.booking.domain.model.valueobjects.BookingStatus
                .CANCELLED.label()).isEqualTo("キャンセル");
    }
}
