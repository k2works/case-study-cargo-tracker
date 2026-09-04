package com.example.cargotracker.routing.domain.model.aggregates;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.cargotracker.routing.domain.model.commands.RegisterVoyageCommand;
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

/** Voyage 集約の不変条件（domain-model.md「Voyage 集約の不変条件」1・4）。 */
class VoyageTest {

    private static final Instant DEPART = Instant.parse("2026-09-10T09:00:00Z");
    private static final Instant ARRIVE = Instant.parse("2026-09-24T18:00:00Z");

    private AxonTestFixture fixture;

    @BeforeEach
    void setUp() {
        EventSourcingConfigurer configurer = EventSourcingConfigurer.create()
                .registerEntity(EventSourcedEntityModule.autodetected(String.class, Voyage.class));
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
}
