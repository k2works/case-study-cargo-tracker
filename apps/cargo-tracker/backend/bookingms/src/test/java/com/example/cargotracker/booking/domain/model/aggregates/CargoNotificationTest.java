package com.example.cargotracker.booking.domain.model.aggregates;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.cargotracker.booking.domain.model.commands.NotifyShipperCommand;
import com.example.cargotracker.booking.domain.model.commands.RequestConditionReviewCommand;
import com.example.cargotracker.booking.domain.model.commands.ReturnToRoutingCommand;
import com.example.cargotracker.booking.domain.model.events.CargoBookedEvent;
import com.example.cargotracker.booking.domain.model.events.CargoRoutedEvent;
import com.example.cargotracker.booking.domain.model.events.ReturnedToRoutingEvent;
import com.example.cargotracker.booking.domain.model.events.RoutingRequestedEvent;
import com.example.cargotracker.booking.domain.model.events.ShipperNotifiedEvent;
import com.example.cargotracker.booking.domain.model.valueobjects.BookingStatus;
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
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

/**
 * 確定経路の荷主への通知（UC10 / US12）と、経路設計への差し戻し（UC08）。
 *
 * <p>送信基盤はスコープ外だが、<b>記録は業務の守りとして働く</b>——経路が決まって
 * いない予約は通知できず、通知していない予約は確定へ進めない。</p>
 */
class CargoNotificationTest {

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

    private static NotifyShipperCommand notifyCommand() {
        return new NotifyShipperCommand("B-0001", "shipper@example.com",
                "JPTYO → USNYC / 14 日 / 2026-09-24 着", "sales01");
    }

    private static ShipperNotifiedEvent notified() {
        return new ShipperNotifiedEvent("B-0001", "shipper@example.com",
                "JPTYO → USNYC / 14 日 / 2026-09-24 着", "sales01", NOW);
    }

    @Test
    @DisplayName("US12: 経路が決まった予約を荷主へ通知できる")
    void notifiesShipper() {
        fixture.given().events(booked(), new RoutingRequestedEvent("B-0001", "sales01"), routed())
                .when().command(notifyCommand())
                .then().success()
                .events(notified());
    }

    @Test
    @DisplayName("US12: 経路が決まっていない予約は通知できない（画面が 500 にならない）")
    void rejectsNotificationBeforeRouting() {
        // 通知は「この経路で運びます」と伝えること。経路が無いまま伝えると、
        // 荷主は何も確認できない。
        fixture.given().events(booked(), new RoutingRequestedEvent("B-0001", "sales01"))
                .when().command(notifyCommand())
                .then().exceptionSatisfies(e ->
                        assertThat(e.getMessage()).contains("通知できません"));
    }

    @Test
    @DisplayName("US12: 再通知できる（条件が変わったら伝え直す）")
    void notifiesAgain() {
        fixture.given().events(booked(), new RoutingRequestedEvent("B-0001", "sales01"),
                        routed(), notified())
                .when().command(notifyCommand())
                .then().success()
                .events(notified());
    }

    @Test
    @DisplayName("宛先の無い通知は受け付けない（誰に伝えたか分からない記録を残さない）")
    void rejectsNotificationWithoutRecipient() {
        fixture.given().events(booked(), new RoutingRequestedEvent("B-0001", "sales01"), routed())
                .when().command(new NotifyShipperCommand("B-0001", " ", "要約", "sales01"))
                .then().exceptionSatisfies(e ->
                        assertThat(e.getMessage()).contains("宛先"));
    }

    @Test
    @DisplayName("要約の無い通知は受け付けない（何を伝えたか分からない記録を残さない）")
    void rejectsNotificationWithoutSummary() {
        // 荷主から「聞いていない」と言われたときに突き合わせられない。
        fixture.given().events(booked(), new RoutingRequestedEvent("B-0001", "sales01"), routed())
                .when().command(new NotifyShipperCommand("B-0001", "s@example.com", " ", "sales01"))
                .then().exceptionSatisfies(e ->
                        assertThat(e.getMessage()).contains("通知内容"));
    }

    @Test
    @DisplayName("受け付けていない予約は通知できない")
    void rejectsNotificationForUnknownBooking() {
        fixture.given().noPriorActivity()
                .when().command(notifyCommand())
                .then().exceptionSatisfies(e ->
                        assertThat(e.getMessage()).contains("受け付けていません"));
    }

    @Test
    @DisplayName("US12: 通知した予約を経路設計へ戻せる")
    void returnsToRouting() {
        fixture.given().events(booked(), new RoutingRequestedEvent("B-0001", "sales01"),
                        routed(), notified())
                .when().command(new ReturnToRoutingCommand("B-0001", "荷主が経由港の変更を希望", "sales01"))
                .then().success()
                .events(new ReturnedToRoutingEvent("B-0001", "荷主が経由港の変更を希望",
                        "sales01", NOW));
    }

    @Test
    @DisplayName("「引き渡した」と「通知後に戻した」はイベント履歴で区別できる")
    void returnEmitsItsOwnEvent() {
        // RoutingRequestedEvent を再利用すると、経路設計者は「なぜ戻ってきたのか」を
        // 読めない。**別のイベントであること**を、型そのもので固定する。
        fixture.given().events(booked(), new RoutingRequestedEvent("B-0001", "sales01"),
                        routed(), notified())
                .when().command(new ReturnToRoutingCommand("B-0001", "変更希望", "sales01"))
                .then().success()
                // 出るイベントを丸ごと比べる。RoutingRequestedEvent を出す実装に
                // 戻すと、型も中身も違うのでここが赤くなる。
                .events(new ReturnedToRoutingEvent("B-0001", "変更希望", "sales01", NOW));
    }

    @Test
    @DisplayName("通知していない予約は経路設計へ戻せない（差し戻しは条件の見直し依頼が持つ）")
    void rejectsReturnBeforeNotification() {
        // 通知前に組み直したいなら、経路設計者が自分で確定し直せばよい。
        // 営業が戻す操作は「荷主が変更を求めた」ことを表す。
        fixture.given().events(booked(), new RoutingRequestedEvent("B-0001", "sales01"), routed())
                .when().command(new ReturnToRoutingCommand("B-0001", "変更希望", "sales01"))
                .then().exceptionSatisfies(e ->
                        assertThat(e.getMessage()).contains("経路設計へ戻せません"));
    }

    @Test
    @DisplayName("理由の無い差し戻しは受け付けない（経路設計者が何を直すか分からない）")
    void rejectsReturnWithoutReason() {
        fixture.given().events(booked(), new RoutingRequestedEvent("B-0001", "sales01"),
                        routed(), notified())
                .when().command(new ReturnToRoutingCommand("B-0001", "  ", "sales01"))
                .then().exceptionSatisfies(e ->
                        assertThat(e.getMessage()).contains("理由"));
    }

    @Test
    @DisplayName("戻したあとは経路を確定し直せる（旅程は残るが設計依頼中に戻る）")
    void routeCanBeAssignedAfterReturn() {
        fixture.given().events(booked(), new RoutingRequestedEvent("B-0001", "sales01"),
                        routed(), notified(),
                        new ReturnedToRoutingEvent("B-0001", "変更希望", "sales01", NOW))
                .when().command(new com.example.cargotracker.booking.domain.model.commands
                        .AssignRouteCommand("B-0001",
                        new com.example.cargotracker.booking.domain.model.valueobjects
                                .CargoItinerary(List.of(
                                new com.example.cargotracker.booking.domain.model.valueobjects.Leg(
                                        "V-2",
                                        com.example.cargotracker.shared.domain.location.Location
                                                .of("JPTYO"),
                                        com.example.cargotracker.shared.domain.location.Location
                                                .of("USNYC"),
                                        Instant.parse("2026-10-01T00:00:00Z"),
                                        Instant.parse("2026-10-20T00:00:00Z")))),
                        "routing01"))
                .then().success();
    }

    @Test
    @DisplayName("戻した予約は差し戻せない（条件の見直し依頼とは別の経路）")
    void returnedBookingCannotAlsoBeSentBackForReview() {
        // 戻した直後は routingStatus が ROUTING_REQUESTED なので、経路設計者は
        // そこから条件の見直しを頼める。**業務としてはこれでよい**——組めなければ
        // 営業へ返る。この検査は、その導線が繋がっていることを固定する。
        fixture.given().events(booked(), new RoutingRequestedEvent("B-0001", "sales01"),
                        routed(), notified(),
                        new ReturnedToRoutingEvent("B-0001", "変更希望", "sales01", NOW))
                .when().command(new RequestConditionReviewCommand("B-0001",
                        "希望の経由港では期限に間に合いません", "routing01"))
                .then().success();
    }

    @ParameterizedTest
    @EnumSource(BookingStatus.class)
    @DisplayName("通知できるのは遷移表が通知済みへ進める状態だけ（後退させない）")
    void notificationFollowsTheTransitionTable(BookingStatus status) {
        // **routingStatus だけを見ると後退が起きる。** 確定済み（US13・IT7）や
        // 終端（精算済・キャンセル）の予約に再通知すると、遷移表に無い
        // 「確定 → 通知済み」が静かに起きる（IT6 レビュー 中・2 視点）。
        //
        // 集約のイベントでは到達できない状態があるので、遷移表の述語そのものを
        // 全値で回して固定する。値を足したらここが赤くなる。
        assertThat(status.canTransitionTo(BookingStatus.ROUTE_NOTIFIED))
                .as("%s から通知済みへ進めるか", status)
                .isEqualTo(status == BookingStatus.ROUTE_PROPOSED
                        || status == BookingStatus.ROUTE_NOTIFIED);
    }
}
