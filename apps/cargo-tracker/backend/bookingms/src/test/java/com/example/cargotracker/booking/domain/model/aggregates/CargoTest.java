package com.example.cargotracker.booking.domain.model.aggregates;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.cargotracker.booking.domain.model.commands.BookCargoCommand;
import com.example.cargotracker.booking.domain.model.events.CargoBookedEvent;
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

    private AxonTestFixture fixture;

    @BeforeEach
    void setUp() {
        EventSourcingConfigurer configurer = EventSourcingConfigurer.create()
                .registerEntity(EventSourcedEntityModule.autodetected(String.class, Cargo.class));
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
}
