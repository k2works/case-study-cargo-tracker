package com.example.cargotracker.routing.domain.model.aggregates;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.cargotracker.routing.domain.model.commands.RegisterVoyageCommand;
import com.example.cargotracker.routing.domain.model.commands.UpdateVoyageScheduleCommand;
import com.example.cargotracker.routing.domain.model.events.VoyageCancelledEvent;
import com.example.cargotracker.routing.domain.model.events.VoyageScheduleUpdatedEvent;
import com.example.cargotracker.routing.domain.model.events.VoyageRegisteredEvent;
import com.example.cargotracker.routing.domain.model.valueobjects.CargoType;
import com.example.cargotracker.routing.domain.model.valueobjects.Carrier;
import com.example.cargotracker.routing.domain.model.valueobjects.CarrierMovement;
import com.example.cargotracker.routing.domain.model.valueobjects.Schedule;
import com.example.cargotracker.routing.domain.model.valueobjects.VesselName;
import com.example.cargotracker.shared.domain.location.Location;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import org.axonframework.eventsourcing.configuration.EventSourcedEntityModule;
import org.axonframework.eventsourcing.configuration.EventSourcingConfigurer;
import org.axonframework.test.fixture.AxonTestFixture;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Voyage 集約の不変条件（domain-model.md「Voyage 集約の不変条件」1・4・5）。 */
class VoyageTest {

    private static final Instant DEPART = Instant.parse("2026-09-10T09:00:00Z");
    private static final Instant ARRIVE = Instant.parse("2026-09-24T18:00:00Z");

    private AxonTestFixture fixture;

    @BeforeEach
    void setUp() {
        EventSourcingConfigurer configurer = EventSourcingConfigurer.create()
                .registerEntity(EventSourcedEntityModule.autodetected(String.class, Voyage.class))
                // 「いつ直したか」は集約が時計から採ってイベントに載せる。固定しないと
                // 期待するイベントを書けない（投影が現在時刻で決めると読み直しで動く）。
                .componentRegistry(registry -> registry.registerComponent(
                        java.time.Clock.class,
                        c -> java.time.Clock.fixed(UPDATED_AT, java.time.ZoneOffset.UTC)));
        fixture = AxonTestFixture.with(configurer, c -> c.disableAxonServer());
    }

    private static Schedule tokyoToNewYork() {
        return new Schedule(List.of(new CarrierMovement(
                Location.of("JPTYO"), Location.of("USNYC"), DEPART, ARRIVE)));
    }

    private static RegisterVoyageCommand register(Set<CargoType> cargoTypes) {
        return new RegisterVoyageCommand("V-MOL-001",
                new Carrier("MOL", "商船三井"),
                new VesselName("MOL EXPRESS"),
                tokyoToNewYork(),
                cargoTypes,
                "routing01");
    }

    private static VoyageRegisteredEvent registered(List<String> cargoTypes) {
        return new VoyageRegisteredEvent("V-MOL-001", "MOL", "商船三井", "MOL EXPRESS",
                List.of(new VoyageRegisteredEvent.Movement("JPTYO", "USNYC", DEPART, ARRIVE)),
                cargoTypes, "routing01");
    }

    @Test
    @DisplayName("航海を登録すると VoyageRegisteredEvent が出る")
    void registers() {
        fixture.given().noPriorActivity()
                .when().command(register(Set.of(CargoType.GENERAL, CargoType.HAZARDOUS)))
                .then().success()
                .events(registered(List.of("GENERAL", "HAZARDOUS")));
    }

    @Test
    @DisplayName("不変条件 4: 対応貨物種別を選ばないと一般貨物のみになる")
    void defaultsToGeneral() {
        fixture.given().noPriorActivity()
                .when().command(register(Set.of()))
                .then().success()
                .events(registered(List.of("GENERAL")));
    }

    @Test
    @DisplayName("不変条件 1: 同じ航海番号は 2 度登録できない")
    void rejectsDuplicate() {
        // イベントから復元した集約が航海番号を持っていれば、2 度目は断られる。
        // @EventTag が抜けていると空のまま復元され、この検査は素通りする。
        fixture.given().event(registered(List.of("GENERAL")))
                .when().command(register(Set.of(CargoType.GENERAL)))
                .then().exceptionSatisfies(e ->
                        assertThat(e.getMessage()).contains("既に登録されています"));
    }

    @Test
    @DisplayName("航海番号が空白なら受け付けない")
    void rejectsBlankVoyageNumber() {
        // null は @TargetEntityId の解決で先に落ちるので、コマンドバス越しには
        // ここまで届かない。届く形（空白）だけを見る。
        fixture.given().noPriorActivity()
                .when().command(new RegisterVoyageCommand(" ", new Carrier("MOL", "商船三井"),
                        new VesselName("MOL EXPRESS"), tokyoToNewYork(), Set.of(), "routing01"))
                .then().exceptionSatisfies(e ->
                        assertThat(e.getMessage()).contains("航海番号は必須です"));
    }

    @Test
    @DisplayName("航海番号が 20 文字を超えるなら集約が断る")
    void rejectsTooLongVoyageNumber() {
        // 値オブジェクトに書いてあるだけでは守られない。集約を素通りすると
        // イベントになり、投影の VARCHAR(20) で落ちて投影全体が止まる。
        fixture.given().noPriorActivity()
                .when().command(new RegisterVoyageCommand("V".repeat(21),
                        new Carrier("MOL", "商船三井"), new VesselName("MOL EXPRESS"),
                        tokyoToNewYork(), Set.of(), "routing01"))
                .then().exceptionSatisfies(e ->
                        assertThat(e.getMessage()).contains("航海番号は 20 文字以内です"));
    }

    @Test
    @DisplayName("運送会社・船名・寄港地はいずれも欠かせない")
    void rejectsMissingParts() {
        fixture.given().noPriorActivity()
                .when().command(new RegisterVoyageCommand("V-MOL-001", null,
                        new VesselName("MOL EXPRESS"), tokyoToNewYork(), Set.of(), "routing01"))
                .then().exceptionSatisfies(e ->
                        assertThat(e.getMessage()).contains("運送会社は必須です"));

        fixture.given().noPriorActivity()
                .when().command(new RegisterVoyageCommand("V-MOL-001",
                        new Carrier("MOL", "商船三井"), null, tokyoToNewYork(), Set.of(),
                        "routing01"))
                .then().exceptionSatisfies(e ->
                        assertThat(e.getMessage()).contains("船名は必須です"));

        fixture.given().noPriorActivity()
                .when().command(new RegisterVoyageCommand("V-MOL-001",
                        new Carrier("MOL", "商船三井"), new VesselName("MOL EXPRESS"), null,
                        Set.of(), "routing01"))
                .then().exceptionSatisfies(e ->
                        assertThat(e.getMessage()).contains("寄港地を 1 件以上"));
    }

    @Test
    @DisplayName("対応貨物種別が null でも一般貨物のみになる")
    void nullCargoTypesDefaultsToGeneral() {
        // 空集合と null は別の分岐。片方だけ見ると、もう片方を消しても緑になる。
        fixture.given().noPriorActivity()
                .when().command(register(null))
                .then().success()
                .events(registered(List.of("GENERAL")));
    }

    private static final Instant UPDATED_AT = Instant.parse("2026-09-05T00:00:00Z");

    private static final Instant DEPART_LATER = Instant.parse("2026-09-12T09:00:00Z");
    private static final Instant ARRIVE_LATER = Instant.parse("2026-09-26T18:00:00Z");

    private static Schedule delayedSchedule() {
        return new Schedule(List.of(new CarrierMovement(
                Location.of("JPTYO"), Location.of("USNYC"), DEPART_LATER, ARRIVE_LATER)));
    }

    private static UpdateVoyageScheduleCommand update(Schedule schedule,
            Set<CargoType> cargoTypes) {
        return new UpdateVoyageScheduleCommand("V-MOL-001",
                new Carrier("MOL", "商船三井"),
                new VesselName("MOL EXPRESS"),
                schedule,
                cargoTypes,
                "routing01");
    }

    @Test
    @DisplayName("US25: 登録済みの航海はスケジュールを更新できる")
    void updatesSchedule() {
        fixture.given().event(registered(List.of("GENERAL")))
                .when().command(update(delayedSchedule(), Set.of(CargoType.GENERAL)))
                .then().success()
                .events(new VoyageScheduleUpdatedEvent("V-MOL-001", "MOL", "商船三井",
                        "MOL EXPRESS",
                        List.of(new VoyageScheduleUpdatedEvent.Movement(
                                "JPTYO", "USNYC", DEPART_LATER, ARRIVE_LATER)),
                        List.of("GENERAL"), "routing01", UPDATED_AT));
    }

    @Test
    @DisplayName("登録していない航海は更新できない")
    void rejectsUpdateOfUnknownVoyage() {
        // 空のまま復元された集約に更新を通すと、登録を経ていない航海が
        // イベントだけで生まれる。
        fixture.given().noPriorActivity()
                .when().command(update(delayedSchedule(), Set.of(CargoType.GENERAL)))
                .then().exceptionSatisfies(e ->
                        assertThat(e.getMessage()).contains("登録されていません"));
    }

    @Test
    @DisplayName("不変条件 5: キャンセル済みの航海は更新できない")
    void rejectsUpdateOfCancelledVoyage() {
        fixture.given().events(registered(List.of("GENERAL")),
                        new VoyageCancelledEvent("V-MOL-001", "運航中止", "routing01"))
                .when().command(update(delayedSchedule(), Set.of(CargoType.GENERAL)))
                .then().exceptionSatisfies(e ->
                        assertThat(e.getMessage()).contains("キャンセル"));
    }

    @Test
    @DisplayName("更新は登録と同じ検査を通る")
    void updateIsValidatedLikeRegistration() {
        // 「登録では断るのに更新では通る」を作らない。検査を 2 か所に書くと必ずずれる。
        fixture.given().event(registered(List.of("GENERAL")))
                .when().command(new UpdateVoyageScheduleCommand("V-MOL-001", null,
                        new VesselName("MOL EXPRESS"), delayedSchedule(), Set.of(), "routing01"))
                .then().exceptionSatisfies(e ->
                        assertThat(e.getMessage()).contains("運送会社は必須です"));

        fixture.given().event(registered(List.of("GENERAL")))
                .when().command(new UpdateVoyageScheduleCommand("V-MOL-001",
                        new Carrier("MOL", "商船三井"), null, delayedSchedule(), Set.of(),
                        "routing01"))
                .then().exceptionSatisfies(e ->
                        assertThat(e.getMessage()).contains("船名は必須です"));
    }

    @Test
    @DisplayName("更新でも対応貨物種別を選ばないと一般貨物のみになる")
    void updateDefaultsToGeneral() {
        fixture.given().event(registered(List.of("GENERAL", "HAZARDOUS")))
                .when().command(update(delayedSchedule(), Set.of()))
                .then().success()
                .events(new VoyageScheduleUpdatedEvent("V-MOL-001", "MOL", "商船三井",
                        "MOL EXPRESS",
                        List.of(new VoyageScheduleUpdatedEvent.Movement(
                                "JPTYO", "USNYC", DEPART_LATER, ARRIVE_LATER)),
                        List.of("GENERAL"), "routing01", UPDATED_AT));
    }
}
