package com.example.cargotracker.routing.domain.model.valueobjects;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.cargotracker.shared.domain.error.BusinessRuleViolation;
import com.example.cargotracker.shared.domain.location.Location;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 探索の条件と区間の不変条件（US08）。
 *
 * <p>断る側を確かめないと、通す側だけが緑になる。壊れた条件は「候補 0 件」として
 * 正常に見えてしまい、経路設計者は直らない条件を変え続ける。</p>
 */
class RoutingValueObjectsTest {

    private static final LocalDate DEADLINE = LocalDate.of(2026, 10, 31);
    private static final Instant LOAD = Instant.parse("2026-09-10T00:00:00Z");
    private static final Instant UNLOAD = Instant.parse("2026-09-24T00:00:00Z");
    private static final Location TOKYO = Location.of("JPTYO");
    private static final Location NEWYORK = Location.of("USNYC");

    private static RouteSearchSpecification spec(
            Location origin, Location destination, LocalDate deadline,
            CargoType type, Set<Location> exclude, Location departFrom) {
        return new RouteSearchSpecification(
                origin, destination, deadline, type, exclude, departFrom);
    }

    @Test
    @DisplayName("端点・期限・貨物種別が揃っていない条件は断る")
    void rejectsIncompleteConditions() {
        assertThatThrownBy(() -> spec(null, NEWYORK, DEADLINE,
                CargoType.GENERAL, Set.of(), null))
                .isInstanceOf(BusinessRuleViolation.class).hasMessageContaining("出発地と目的地");
        assertThatThrownBy(() -> spec(TOKYO, null, DEADLINE,
                CargoType.GENERAL, Set.of(), null))
                .isInstanceOf(BusinessRuleViolation.class).hasMessageContaining("出発地と目的地");
        assertThatThrownBy(() -> spec(TOKYO, TOKYO, DEADLINE,
                CargoType.GENERAL, Set.of(), null))
                .isInstanceOf(BusinessRuleViolation.class);
        assertThatThrownBy(() -> spec(TOKYO, NEWYORK, null,
                CargoType.GENERAL, Set.of(), null))
                .isInstanceOf(BusinessRuleViolation.class).hasMessageContaining("到着期限");
        assertThatThrownBy(() -> spec(TOKYO, NEWYORK, DEADLINE, null, Set.of(), null))
                .isInstanceOf(BusinessRuleViolation.class).hasMessageContaining("貨物種別");
    }

    @Test
    @DisplayName("端点や起点を除外した条件は断る（必ず 0 件になる条件を黙って受けない）")
    void rejectsExcludingItsOwnEndpoints() {
        assertThatThrownBy(() -> spec(TOKYO, NEWYORK, DEADLINE,
                CargoType.GENERAL, Set.of(TOKYO), null))
                .isInstanceOf(BusinessRuleViolation.class).hasMessageContaining("除外できません");
        assertThatThrownBy(() -> spec(TOKYO, NEWYORK, DEADLINE,
                CargoType.GENERAL, Set.of(NEWYORK), null))
                .isInstanceOf(BusinessRuleViolation.class).hasMessageContaining("除外できません");
        assertThatThrownBy(() -> spec(TOKYO, NEWYORK, DEADLINE, CargoType.GENERAL,
                Set.of(Location.of("SGSIN")), Location.of("SGSIN")))
                .isInstanceOf(BusinessRuleViolation.class).hasMessageContaining("除外できません");
    }

    @Test
    @DisplayName("除外港を渡さない条件はそのまま作れる")
    void acceptsConditionsWithoutExclusions() {
        assertThatCode(() -> spec(TOKYO, NEWYORK, DEADLINE, CargoType.GENERAL, null, null))
                .doesNotThrowAnyException();
        assertThatCode(() -> spec(TOKYO, NEWYORK, DEADLINE, CargoType.HAZARDOUS,
                Set.of(Location.of("SGSIN")), Location.of("NLRTM")))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("航海番号・積地揚地・日時が揃っていない区間は作れない")
    void edgeRequiresEveryField() {
        assertThatThrownBy(() -> new TransitEdge(" ", TOKYO, NEWYORK, LOAD, UNLOAD))
                .isInstanceOf(BusinessRuleViolation.class).hasMessageContaining("航海番号");
        assertThatThrownBy(() -> new TransitEdge("V-1", null, NEWYORK, LOAD, UNLOAD))
                .isInstanceOf(BusinessRuleViolation.class).hasMessageContaining("積地と揚地");
        assertThatThrownBy(() -> new TransitEdge("V-1", TOKYO, null, LOAD, UNLOAD))
                .isInstanceOf(BusinessRuleViolation.class).hasMessageContaining("積地と揚地");
        assertThatThrownBy(() -> new TransitEdge("V-1", TOKYO, NEWYORK, null, UNLOAD))
                .isInstanceOf(BusinessRuleViolation.class).hasMessageContaining("積込日時");
        assertThatThrownBy(() -> new TransitEdge("V-1", TOKYO, NEWYORK, LOAD, null))
                .isInstanceOf(BusinessRuleViolation.class).hasMessageContaining("積込日時");
    }

    @Test
    @DisplayName("同じ港を結ぶ区間・荷揚が先の区間は作れない")
    void edgeMustMoveForwardBetweenDifferentPorts() {
        assertThatThrownBy(() -> new TransitEdge("V-1", TOKYO, TOKYO, LOAD, UNLOAD))
                .isInstanceOf(BusinessRuleViolation.class).hasMessageContaining("JPTYO");
        assertThatThrownBy(() -> new TransitEdge("V-1", TOKYO, NEWYORK, UNLOAD, LOAD))
                .isInstanceOf(BusinessRuleViolation.class);
        assertThatThrownBy(() -> new TransitEdge("V-1", TOKYO, NEWYORK, LOAD, LOAD))
                .isInstanceOf(BusinessRuleViolation.class);
    }
}
