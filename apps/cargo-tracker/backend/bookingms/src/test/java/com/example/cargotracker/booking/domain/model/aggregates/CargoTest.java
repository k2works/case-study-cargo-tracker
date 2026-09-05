package com.example.cargotracker.booking.domain.model.aggregates;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.cargotracker.booking.domain.model.commands.BookCargoCommand;
import com.example.cargotracker.booking.domain.model.commands.RequestRoutingCommand;
import com.example.cargotracker.booking.domain.model.commands.UpdateCargoSpecificationCommand;
import com.example.cargotracker.booking.domain.model.events.CargoSpecificationUpdatedEvent;
import com.example.cargotracker.booking.domain.model.events.CargoBookedEvent;
import com.example.cargotracker.booking.domain.model.events.RoutingRequestedEvent;
import com.example.cargotracker.booking.domain.model.valueobjects.BookingStatus;
import com.example.cargotracker.booking.domain.model.valueobjects.CargoSpecification;
import com.example.cargotracker.booking.domain.model.valueobjects.CargoType;
import com.example.cargotracker.booking.domain.model.valueobjects.Dimensions;
import com.example.cargotracker.booking.domain.model.valueobjects.HazardousDeclaration;
import com.example.cargotracker.booking.domain.model.valueobjects.RouteSpecification;
import com.example.cargotracker.booking.domain.model.valueobjects.TemperatureRequirement;
import com.example.cargotracker.booking.domain.model.valueobjects.Weight;
import com.example.cargotracker.shared.domain.location.Location;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.Month;
import org.axonframework.eventsourcing.configuration.EventSourcedEntityModule;
import org.axonframework.eventsourcing.configuration.EventSourcingConfigurer;
import org.axonframework.test.fixture.AxonTestFixture;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Cargo 集約の不変条件（domain-model.md「Cargo 集約の不変条件」1・2・3）。 */
class CargoTest {

    private static final LocalDate DEADLINE = LocalDate.of(2026, Month.DECEMBER, 1);

    /** 集約が「いつ直したか」を採る時計。固定しないと期待するイベントを書けない。 */
    private static final java.time.Instant UPDATED_AT =
            java.time.Instant.parse("2026-09-04T00:00:00Z");

    private AxonTestFixture fixture;

    @BeforeEach
    void setUp() {
        EventSourcingConfigurer configurer = EventSourcingConfigurer.create()
                .registerEntity(EventSourcedEntityModule.autodetected(String.class, Cargo.class))
                // 集約が「今日」を決めるのに使う時計。固定しないと、期限の検査が
                // テストを回した日で結果を変える。
                .componentRegistry(registry -> registry.registerComponent(
                        java.time.Clock.class,
                        c -> java.time.Clock.fixed(
                                java.time.Instant.parse("2026-09-04T00:00:00Z"),
                                java.time.ZoneId.of("Asia/Tokyo"))));
        fixture = AxonTestFixture.with(configurer, c -> c.disableAxonServer());
    }

    private static CargoSpecification general() {
        return new CargoSpecification(CargoType.GENERAL, Weight.ofKilograms("1200"),
                Dimensions.of("120", "80", "100"), 10, "自動車部品", null, null);
    }

    private static RouteSpecification route() {
        return new RouteSpecification(Location.of("JPTYO"), Location.of("USNYC"), DEADLINE);
    }

    private static BookCargoCommand book(CargoSpecification spec, RouteSpecification route) {
        return new BookCargoCommand("B-0001", "SHP-000001", spec, route, "sales01");
    }

    @Test
    @DisplayName("予約を受け付けると CargoBookedEvent が出る")
    void books() {
        fixture.given().noPriorActivity()
                .when().command(book(general(), route()))
                .then().success()
                .events(new CargoBookedEvent("B-0001", "SHP-000001", "JPTYO", "USNYC", DEADLINE,
                        "GENERAL", new BigDecimal("1200"), new BigDecimal("120"),
                        new BigDecimal("80"), new BigDecimal("100"), 10, "自動車部品",
                        null, null, null, null, "sales01"));
    }

    @Test
    @DisplayName("荷主 ID が無い予約は受け付けない")
    void requiresShipper() {
        // 荷主の分からない予約は、通知も請求も宛先が無い。
        fixture.given().noPriorActivity()
                .when().command(new BookCargoCommand("B-0001", null, general(), route(), "sales01"))
                .then().exception(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("出発地と目的地が同じ経路仕様は作れない")
    void rejectsSameOriginAndDestination() {
        // 集約の入口ではなく値オブジェクトで断る。ここを通すと、経路探索が
        // 「出発地から出発地への便」を探し続ける。
        assertThatThrownBy(() -> new RouteSpecification(
                Location.of("JPTYO"), Location.of("JPTYO"), DEADLINE))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("出発地と目的地が同じ");
    }

    @Test
    @DisplayName("危険物には申告が要る")
    void requiresHazardousDeclaration() {
        assertThatThrownBy(() -> new CargoSpecification(CargoType.HAZARDOUS,
                Weight.ofKilograms("100"), Dimensions.of("10", "10", "10"), 1, "塗料", null, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("危険物申告");
    }

    @Test
    @DisplayName("冷凍・冷蔵には温度条件が要る")
    void requiresTemperature() {
        assertThatThrownBy(() -> new CargoSpecification(CargoType.REFRIGERATED,
                Weight.ofKilograms("100"), Dimensions.of("10", "10", "10"), 1, "冷凍食品", null, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("温度管理条件");
    }

    @Test
    @DisplayName("種別と付帯情報が食い違う仕様は作れない")
    void rejectsMismatchedExtras() {
        // 一般貨物に危険物申告が付いていると、経路設計が「危険物対応の航海だけ」に
        // 絞るべきか判断できない。
        assertThatThrownBy(() -> new CargoSpecification(CargoType.GENERAL,
                Weight.ofKilograms("100"), Dimensions.of("10", "10", "10"), 1, "部品",
                new HazardousDeclaration("3", "UN1263"), null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new CargoSpecification(CargoType.GENERAL,
                Weight.ofKilograms("100"), Dimensions.of("10", "10", "10"), 1, "部品", null,
                TemperatureRequirement.of("-20", "-10")))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("危険物の予約は申告つきで受け付ける")
    void booksHazardous() {
        CargoSpecification spec = new CargoSpecification(CargoType.HAZARDOUS,
                Weight.ofKilograms("100"), Dimensions.of("10", "20", "30"), 2, "塗料",
                new HazardousDeclaration("3", "UN1263"), null);

        fixture.given().noPriorActivity()
                .when().command(book(spec, route()))
                .then().success()
                .events(new CargoBookedEvent("B-0001", "SHP-000001", "JPTYO", "USNYC", DEADLINE,
                        "HAZARDOUS", new BigDecimal("100"), new BigDecimal("10"),
                        new BigDecimal("20"), new BigDecimal("30"), 2, "塗料",
                        "3", "UN1263", null, null, "sales01"));
    }

    // 「同じ予約を 2 度受け付けない」は AxonTestFixture では判別できない。
    // disableAxonServer() の器では DCB のタグ復元が働かず、given() で積んだ
    // 事前活動を when() の集約が見ないため、守っていてもいなくても緑になる
    // （IT2 で実測）。実 Axon Server で確かめる（CargoBookingIT）。

    @Test
    @DisplayName("必須の入力が欠けた予約は受け付けない")
    void rejectsMissingFields() {
        // 入口の @NotBlank を通り抜けた要求でも、集約が最後に断る。
        //
        // 予約 ID が空のときは集約まで届かない。@TargetEntityId が解決できず
        // EntityIdResolutionException で先に落ちる（IT2 で実測）。集約側の検査は
        // 残すが、ここで確かめられるのは届く経路だけ。
        fixture.given().noPriorActivity()
                .when().command(new BookCargoCommand("B-0001", " ", general(), route(), "sales01"))
                .then().exception(IllegalArgumentException.class);
        fixture.given().noPriorActivity()
                .when().command(new BookCargoCommand("B-0001", "SHP-1", null, route(), "sales01"))
                .then().exception(IllegalArgumentException.class);
        fixture.given().noPriorActivity()
                .when().command(new BookCargoCommand("B-0001", "SHP-1", general(), null, "sales01"))
                .then().exception(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("冷凍の予約は温度条件つきで受け付ける")
    void booksRefrigerated() {
        CargoSpecification spec = new CargoSpecification(CargoType.REFRIGERATED,
                Weight.ofKilograms("500"), Dimensions.of("50", "40", "30"), 3, "冷凍食品",
                null, TemperatureRequirement.of("-20", "-10"));

        fixture.given().noPriorActivity()
                .when().command(book(spec, route()))
                .then().success()
                .events(new CargoBookedEvent("B-0001", "SHP-000001", "JPTYO", "USNYC", DEADLINE,
                        "REFRIGERATED", new BigDecimal("500"), new BigDecimal("50"),
                        new BigDecimal("40"), new BigDecimal("30"), 3, "冷凍食品",
                        null, null, new BigDecimal("-20"), new BigDecimal("-10"), "sales01"));
    }

    @Test
    @DisplayName("到着期限が過去の予約は受け付けない")
    void rejectsPastDeadline() {
        // 年を打ち間違えた予約が仮受付として一覧に載ると、経路設計者が
        // 「間に合う経路が 1 本も出ない」と気づくまで進む。
        fixture.given().noPriorActivity()
                .when().command(book(general(), new RouteSpecification(
                        Location.of("JPTYO"), Location.of("USNYC"),
                        LocalDate.of(2025, Month.DECEMBER, 1))))
                .then().exception(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("到着期限が当日の予約は受け付ける")
    void acceptsTodayAsDeadline() {
        // 当日着は「間に合う」扱い（不変条件 5）。境目を過去側に含めると、
        // その日のうちに着く便を断ることになる。
        fixture.given().noPriorActivity()
                .when().command(book(general(), new RouteSpecification(
                        Location.of("JPTYO"), Location.of("USNYC"),
                        LocalDate.of(2026, Month.SEPTEMBER, 4))))
                .then().success();
    }

    @Test
    @DisplayName("状態遷移の判定は 1 か所に置く")
    void transitionsFromPreliminary() {
        // 画面のボタン出し分けはこの述語を呼ぶ。判定を書き直すと、片方だけ
        // 直したときに画面と集約の判断が食い違う。
        assertThat(BookingStatus.PRELIMINARY.canTransitionTo(BookingStatus.ROUTE_PROPOSED)).isTrue();
        assertThat(BookingStatus.PRELIMINARY.canTransitionTo(BookingStatus.CANCELLED)).isTrue();
        assertThat(BookingStatus.PRELIMINARY.canTransitionTo(BookingStatus.CONFIRMED))
                .as("通知していない予約は確定できない（不変条件 6）")
                .isFalse();
        assertThat(BookingStatus.PRELIMINARY.cancellableImmediately()).isTrue();
        assertThat(BookingStatus.IN_TRANSIT.cancellableImmediately())
                .as("輸送中は申請 → 承認の 2 段階（不変条件 9）")
                .isFalse();
        assertThat(BookingStatus.DELIVERED.canTransitionTo(BookingStatus.CANCELLED))
                .as("引取済以降はキャンセルできない")
                .isFalse();
    }

    private static CargoBookedEvent bookedEvent() {
        return new CargoBookedEvent("B-0001", "SHP-000001", "JPTYO", "USNYC", DEADLINE,
                "GENERAL", new BigDecimal("1200"), new BigDecimal("120"),
                new BigDecimal("80"), new BigDecimal("100"), 10, "自動車部品",
                null, null, null, null, "sales01");
    }

    @Test
    @DisplayName("仮受付の予約は経路設計へ引き渡せる（US06）")
    void requestsRouting() {
        fixture.given().event(bookedEvent())
                .when().command(new RequestRoutingCommand("B-0001", "sales01"))
                .then().success()
                .events(new RoutingRequestedEvent("B-0001", "sales01"));
    }

    @Test
    @DisplayName("受け付けていない予約は引き渡せない")
    void rejectsRoutingForUnknownBooking() {
        // @EventTag が抜けていると空のまま復元され、この検査は素通りする。
        fixture.given().noPriorActivity()
                .when().command(new RequestRoutingCommand("B-0001", "sales01"))
                .then().exceptionSatisfies(e ->
                        assertThat(e.getMessage()).contains("受け付けていません"));
    }

    @Test
    @DisplayName("2 度目の引き渡しも受け付ける（ROUTE_PROPOSED → ROUTE_PROPOSED）")
    void routingCanBeRequestedAgain() {
        // 遷移表が ROUTE_PROPOSED → ROUTE_PROPOSED を許している。判定を書き直さず
        // 述語を呼んでいるので、表を変えればここも変わる。
        fixture.given().event(bookedEvent()).event(new RoutingRequestedEvent("B-0001", "s"))
                .when().command(new RequestRoutingCommand("B-0001", "sales01"))
                .then().success();
    }

    @Test
    @DisplayName("予約 ID が空白なら受け付けない")
    void rejectsBlankBookingId() {
        // null は @TargetEntityId の解決で先に落ちるので、コマンドバス越しには
        // ここまで届かない。届く形（空白）だけを見る。
        fixture.given().noPriorActivity()
                .when().command(new BookCargoCommand(" ", "SHP-000001",
                        general(), route(), "sales01"))
                .then().exceptionSatisfies(e ->
                        assertThat(e.getMessage()).contains("予約 ID は必須です"));
    }

    @Test
    @DisplayName("荷主 ID が空でも空白でも受け付けない")
    void rejectsBlankShipperId() {
        for (String shipperId : new String[] {null, " "}) {
            fixture.given().noPriorActivity()
                    .when().command(new BookCargoCommand("B-0001", shipperId,
                            general(), route(), "sales01"))
                    .then().exceptionSatisfies(e ->
                            assertThat(e.getMessage()).contains("荷主 ID は必須です"));
        }
    }

    private static CargoSpecification corrected() {
        return new CargoSpecification(CargoType.GENERAL, Weight.ofKilograms("1500"),
                Dimensions.of("130", "80", "100"), 12, "自動車部品（訂正）", null, null);
    }

    private static UpdateCargoSpecificationCommand update(CargoSpecification spec,
            RouteSpecification route) {
        return new UpdateCargoSpecificationCommand("B-0001", spec, route, "sales02");
    }

    private static CargoBookedEvent booked() {
        return new CargoBookedEvent("B-0001", "SHP-000001", "JPTYO", "USNYC", DEADLINE,
                "GENERAL", new BigDecimal("1200"), new BigDecimal("120"),
                new BigDecimal("80"), new BigDecimal("100"), 10, "自動車部品",
                null, null, null, null, "sales01");
    }

    @Test
    @DisplayName("US32: 仮受付の予約は貨物仕様と輸送条件を修正できる")
    void updatesSpecificationWhilePreliminary() {
        fixture.given().event(booked())
                .when().command(update(corrected(), route()))
                .then().success()
                .events(new CargoSpecificationUpdatedEvent("B-0001", "JPTYO", "USNYC", DEADLINE,
                        "GENERAL", new BigDecimal("1500"), new BigDecimal("130"),
                        new BigDecimal("80"), new BigDecimal("100"), 12, "自動車部品（訂正）",
                        null, null, null, null, "sales02", UPDATED_AT));
    }

    @Test
    @DisplayName("US32: 経路提案中より先へ進んだ予約は修正できない")
    void rejectsUpdateAfterRoutingRequested() {
        // 判定は遷移表の述語を呼ぶ。集約の側で状態を直に比べると、状態が増えた
        // ときに集約と遷移表の判断が食い違う。
        fixture.given().events(booked(), new RoutingRequestedEvent("B-0001", "sales01"))
                .when().command(update(corrected(), route()))
                .then().exceptionSatisfies(e ->
                        assertThat(e.getMessage()).contains("修正できません"));
    }

    @Test
    @DisplayName("受け付けていない予約は修正できない")
    void rejectsUpdateOfUnknownBooking() {
        fixture.given().noPriorActivity()
                .when().command(update(corrected(), route()))
                .then().exceptionSatisfies(e ->
                        assertThat(e.getMessage()).contains("受け付けていません"));
    }

    @Test
    @DisplayName("US32: 修正でも登録と同じ検査が働く")
    void updateIsValidatedLikeBooking() {
        // 「登録では断るのに修正では通る」を作らない。
        fixture.given().event(booked())
                .when().command(new UpdateCargoSpecificationCommand("B-0001", null, route(),
                        "sales02"))
                .then().exceptionSatisfies(e ->
                        assertThat(e.getMessage()).contains("貨物仕様は必須です"));

        fixture.given().event(booked())
                .when().command(new UpdateCargoSpecificationCommand("B-0001", corrected(), null,
                        "sales02"))
                .then().exceptionSatisfies(e ->
                        assertThat(e.getMessage()).contains("輸送条件は必須です"));
    }

    @Test
    @DisplayName("US32: 期限を過ぎた予約でも、期限を据え置けば中身を直せる")
    void allowsUpdateOfExpiredBookingWhenDeadlineIsUnchanged() {
        // 入力の誤りに気づくのはたいてい期限が近づいてから。据え置きにも
        // 「今日以降」を求めると、期限を過ぎた仮受付は品名すら直せない。
        LocalDate past = LocalDate.of(2026, Month.AUGUST, 1);
        CargoBookedEvent bookedWithPastDeadline = new CargoBookedEvent("B-0001", "SHP-000001",
                "JPTYO", "USNYC", past, "GENERAL", new BigDecimal("1200"),
                new BigDecimal("120"), new BigDecimal("80"), new BigDecimal("100"), 10,
                "自動車部品", null, null, null, null, "sales01");

        fixture.given().event(bookedWithPastDeadline)
                .when().command(update(corrected(),
                        new RouteSpecification(Location.of("JPTYO"), Location.of("USNYC"), past)))
                .then().success();
    }

    @Test
    @DisplayName("US32: 修正で到着期限を過去にはできない")
    void rejectsPastDeadlineOnUpdate() {
        fixture.given().event(booked())
                .when().command(update(corrected(), new RouteSpecification(
                        Location.of("JPTYO"), Location.of("USNYC"),
                        LocalDate.of(2020, Month.JANUARY, 1))))
                .then().exceptionSatisfies(e ->
                        assertThat(e.getMessage()).contains("到着期限"));
    }

    @Test
    @DisplayName("US32: 危険物なら申告、冷凍なら温度条件が修正でも要る")
    void updateKeepsTypeSpecificRequirements() {
        // 受入基準 3 の本体。値オブジェクトが守っているが、修正の経路でも
        // 同じ検査を通ることを最も安い場所で固定する。
        assertThatThrownBy(() -> new CargoSpecification(CargoType.HAZARDOUS,
                Weight.ofKilograms("100"), Dimensions.of("10", "10", "10"), 1, "塗料", null, null))
                .hasMessageContaining("危険物申告");
        assertThatThrownBy(() -> new CargoSpecification(CargoType.REFRIGERATED,
                Weight.ofKilograms("100"), Dimensions.of("10", "10", "10"), 1, "冷凍食品",
                null, null))
                .hasMessageContaining("温度管理条件");
    }

    @Test
    @DisplayName("US32: 危険物に直すと申告がイベントに載る")
    void updateCarriesHazardousDeclaration() {
        // 付帯情報は表示のためだけに運ぶ値なので、どこか一層で潰しても
        // 「修正できた」までは緑になる。イベントに載ることを固定する。
        CargoSpecification hazardous = new CargoSpecification(CargoType.HAZARDOUS,
                Weight.ofKilograms("100"), Dimensions.of("10", "10", "10"), 1, "塗料",
                new HazardousDeclaration("3", "UN1263"), null);

        fixture.given().event(booked())
                .when().command(update(hazardous, route()))
                .then().success()
                .events(new CargoSpecificationUpdatedEvent("B-0001", "JPTYO", "USNYC", DEADLINE,
                        "HAZARDOUS", new BigDecimal("100"), new BigDecimal("10"),
                        new BigDecimal("10"), new BigDecimal("10"), 1, "塗料",
                        "3", "UN1263", null, null, "sales02", UPDATED_AT));
    }

    @Test
    @DisplayName("US32: 冷凍・冷蔵に直すと温度条件がイベントに載る")
    void updateCarriesTemperatureRequirement() {
        CargoSpecification refrigerated = new CargoSpecification(CargoType.REFRIGERATED,
                Weight.ofKilograms("100"), Dimensions.of("10", "10", "10"), 1, "冷凍食品",
                null, new TemperatureRequirement(new BigDecimal("-20"), new BigDecimal("-5")));

        fixture.given().event(booked())
                .when().command(update(refrigerated, route()))
                .then().success()
                .events(new CargoSpecificationUpdatedEvent("B-0001", "JPTYO", "USNYC", DEADLINE,
                        "REFRIGERATED", new BigDecimal("100"), new BigDecimal("10"),
                        new BigDecimal("10"), new BigDecimal("10"), 1, "冷凍食品",
                        null, null, new BigDecimal("-20"), new BigDecimal("-5"),
                        "sales02", UPDATED_AT));
    }

    @Test
    @DisplayName("受け付けていない予約は引き渡せない")
    void rejectsRoutingRequestOfUnknownBooking() {
        // 空のまま復元された集約に引き渡しを通すと、受付を経ていない予約が
        // イベントだけで経路提案中になる。
        fixture.given().noPriorActivity()
                .when().command(new RequestRoutingCommand("B-0001", "sales01"))
                .then().exceptionSatisfies(e ->
                        assertThat(e.getMessage()).contains("受け付けていません"));
    }
}
