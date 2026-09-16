package com.example.cargotracker.booking.domain.model.aggregates;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.cargotracker.booking.domain.model.commands.AdjustRouteSpecificationCommand;
import com.example.cargotracker.booking.domain.model.commands.AssignRouteCommand;
import com.example.cargotracker.booking.domain.model.commands.RequestConditionReviewCommand;
import com.example.cargotracker.booking.domain.model.events.CargoBookedEvent;
import com.example.cargotracker.booking.domain.model.events.CargoRoutedEvent;
import com.example.cargotracker.booking.domain.model.events.ConditionReviewRequestedEvent;
import com.example.cargotracker.booking.domain.model.events.RouteSpecificationAdjustedEvent;
import com.example.cargotracker.booking.domain.model.events.RoutingRequestedEvent;
import com.example.cargotracker.booking.domain.model.valueobjects.CargoItinerary;
import com.example.cargotracker.booking.domain.model.valueobjects.Leg;
import com.example.cargotracker.booking.domain.model.valueobjects.RoutingStatus;
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
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

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

    private static RequestConditionReviewCommand review(String reason) {
        return new RequestConditionReviewCommand("B-0001", reason, "routing01");
    }

    @Test
    @DisplayName("US10 §4: 組めないことを営業へ差し戻せる（ADR-0009 決定 1: 状態は動かさない）")
    void requestsConditionReview() {
        fixture.given().events(booked(), new RoutingRequestedEvent("B-0001", "sales01"))
                .when().command(review("期限内に着ける便がありません"))
                .then().success()
                .events(new ConditionReviewRequestedEvent("B-0001",
                        "期限内に着ける便がありません", "routing01", NOW));
    }

    @Test
    @DisplayName("ADR-0009 決定 1: 差し戻しても経路設計作業一覧から消えない（状態を戻さない）")
    void conditionReviewDoesNotResetRoutingStatus() {
        // 状態を NOT_ROUTED へ戻すと「一度も設計していない予約」と混ざる。
        // 差し戻したあとも、営業が条件を直せばそのまま設計を続けられる。
        fixture.given().events(booked(), new RoutingRequestedEvent("B-0001", "sales01"),
                        new ConditionReviewRequestedEvent("B-0001", "組めません",
                                "routing01", NOW))
                .when().command(adjust(EXTENDED))
                .then().success();
    }

    @ParameterizedTest
    @EnumSource(RoutingStatus.class)
    @DisplayName("ADR-0009 決定 2: 差し戻せるのは設計依頼中のときだけ（誤配は含めない）")
    void onlyRoutingRequestedCanBeSentBack(RoutingStatus status) {
        // **誤配（MISROUTED）は含めない。** 誤配は「荷物が経路から外れた」ことで、
        // 条件が組めないこととは別である。差し戻せると、荷物が動いている予約が
        // 営業の手番に見える。再設計は US28（IT11）が持つ。
        //
        // 集約のイベントでは MISROUTED に到達できない（US28 で足す）ので、
        // 列挙の全値を回して述語を固定する。値を足したらここが赤くなる。
        assertThat(status.canRequestConditionReview())
                .as("%s から差し戻せるか", status)
                .isEqualTo(status == RoutingStatus.ROUTING_REQUESTED);
    }

    @Test
    @DisplayName("ADR-0009 決定 3: 経路が決まった予約は差し戻せない（先に条件を調整する）")
    void rejectsConditionReviewAfterRouting() {
        fixture.given().events(booked(), new RoutingRequestedEvent("B-0001", "sales01"),
                        routed())
                .when().command(review("組めません"))
                .then().exceptionSatisfies(e ->
                        assertThat(e.getMessage()).contains("差し戻せません"));
    }

    @Test
    @DisplayName("理由の無い差し戻しは受け付けない（営業が何をすればよいか分からない）")
    void rejectsConditionReviewWithoutReason() {
        fixture.given().events(booked(), new RoutingRequestedEvent("B-0001", "sales01"))
                .when().command(review("  "))
                .then().exceptionSatisfies(e ->
                        assertThat(e.getMessage()).contains("理由"));
    }

    @Test
    @DisplayName("引き渡していない予約は差し戻せない")
    void rejectsConditionReviewBeforeRoutingRequested() {
        fixture.given().event(booked())
                .when().command(review("組めません"))
                .then().exceptionSatisfies(e ->
                        assertThat(e.getMessage()).contains("差し戻せません"));
    }

    @Test
    @DisplayName("除外港を送らなくても調整できる（空の一覧として扱う）")
    void adjustsWithoutExcludedPorts() {
        // null をそのまま持つと、投影と画面で「制限なし」と「未設定」が混ざる。
        fixture.given().events(booked(), new RoutingRequestedEvent("B-0001", "sales01"))
                .when().command(new AdjustRouteSpecificationCommand("B-0001", EXTENDED,
                        null, null, "routing01"))
                .then().success()
                .events(new RouteSpecificationAdjustedEvent("B-0001", EXTENDED,
                        List.of(), null, "routing01", NOW));
    }

    @Test
    @DisplayName("到着期限を送らない調整は受け付けない")
    void rejectsAdjustmentWithoutDeadline() {
        fixture.given().events(booked(), new RoutingRequestedEvent("B-0001", "sales01"))
                .when().command(new AdjustRouteSpecificationCommand("B-0001", null,
                        List.of(), null, "routing01"))
                .then().exceptionSatisfies(e ->
                        assertThat(e.getMessage()).contains("到着期限は必須"));
    }

    @Test
    @DisplayName("出発地も除外港にはできない（目的地だけを見ていない）")
    void rejectsExcludingTheOrigin() {
        fixture.given().events(booked(), new RoutingRequestedEvent("B-0001", "sales01"))
                .when().command(new AdjustRouteSpecificationCommand("B-0001", EXTENDED,
                        List.of("JPTYO"), null, "routing01"))
                .then().exceptionSatisfies(e ->
                        assertThat(e.getMessage()).contains("JPTYO"));
    }

    @Test
    @DisplayName("到着期限が今日ちょうどの調整は通す（当日着を落とさない）")
    void acceptsDeadlineToday() {
        // NOW は 2026-09-06T00:00:00Z＝日本時間の 09:06 09:00。業務タイムゾーンで
        // 日付にするので「今日」は 2026-09-06 になる。
        fixture.given().events(booked(), new RoutingRequestedEvent("B-0001", "sales01"))
                .when().command(new AdjustRouteSpecificationCommand("B-0001",
                        LocalDate.of(2026, Month.SEPTEMBER, 6), List.of(), null, "routing01"))
                .then().success();
    }

    @Test
    @DisplayName("理由が null の差し戻しも受け付けない（空白だけを見ていない）")
    void rejectsConditionReviewWithNullReason() {
        fixture.given().events(booked(), new RoutingRequestedEvent("B-0001", "sales01"))
                .when().command(new RequestConditionReviewCommand("B-0001", null, "routing01"))
                .then().exceptionSatisfies(e ->
                        assertThat(e.getMessage()).contains("理由"));
    }

    @Test
    @DisplayName("受け付けていない予約は差し戻せない")
    void rejectsConditionReviewForUnknownBooking() {
        fixture.given().noPriorActivity()
                .when().command(review("組めません"))
                .then().exceptionSatisfies(e ->
                        assertThat(e.getMessage()).contains("受け付けていません"));
    }

    @Test
    @DisplayName("小文字の港コードは受け付けない（生文字列で比べない）")
    void rejectsLowerCasePortCode() {
        // 生文字列の equals で比べていると、"jptyo" が端点の除外検査を素通りして
        // 「候補が必ず 0 件になる条件」が作られる（IT6 レビュー 高）。Location に
        // 通すと、素通りする前に形で断る。
        fixture.given().events(booked(), new RoutingRequestedEvent("B-0001", "sales01"))
                .when().command(new AdjustRouteSpecificationCommand("B-0001", EXTENDED,
                        List.of("jptyo"), null, "routing01"))
                .then().exceptionSatisfies(e ->
                        assertThat(e.getMessage()).contains("UN/LOCODE"));
    }

    @Test
    @DisplayName("港コードの形が違う起点は受け付けない（投影で落ちる前に断る）")
    void rejectsMalformedDepartFrom() {
        // 6 文字以上はイベントを確定してから投影の UPDATE で落ち、リプレイのたびに
        // 落ち続ける。集約で断れば、そもそもイベントにならない。
        fixture.given().events(booked(), new RoutingRequestedEvent("B-0001", "sales01"))
                .when().command(new AdjustRouteSpecificationCommand("B-0001", EXTENDED,
                        List.of(), "JPTYOX", "routing01"))
                .then().exceptionSatisfies(e ->
                        assertThat(e.getMessage()).contains("UN/LOCODE"));
    }

    @Test
    @DisplayName("形の正しい港コードはそのまま記録する")
    void keepsWellFormedPortCodes() {
        fixture.given().events(booked(), new RoutingRequestedEvent("B-0001", "sales01"))
                .when().command(new AdjustRouteSpecificationCommand("B-0001", EXTENDED,
                        List.of("SGSIN"), "JPOSA", "routing01"))
                .then().success()
                .events(new RouteSpecificationAdjustedEvent("B-0001", EXTENDED,
                        List.of("SGSIN"), "JPOSA", "routing01", NOW));
    }

    @ParameterizedTest
    @EnumSource(RoutingStatus.class)
    @DisplayName("条件を調整できるのは設計依頼中と設定済みだけ（誤配は含めない）")
    void onlyRequestedOrRoutedCanBeAdjusted(RoutingStatus status) {
        // **誤配を含めると、調整が routingStatus を戻して誤配の記録を消す。**
        // 誤配からの再設計は US28（IT11）が持つ判断で、そこを先に縛らない
        // （IT6 レビュー 中）。集約のイベントでは MISROUTED に到達できないので、
        // 述語を全値で回して固定する。
        assertThat(status.canAdjustRouteSpecification())
                .as("%s から条件を調整できるか", status)
                .isEqualTo(status == RoutingStatus.ROUTING_REQUESTED
                        || status == RoutingStatus.ROUTED);
    }
}
