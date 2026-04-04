package com.example.cargotracker.routing;

import com.example.cargotracker.routing.domain.model.CargoType;
import com.example.cargotracker.routing.domain.model.RouteSearchQuery;
import com.example.cargotracker.routing.domain.model.Voyage;
import com.example.cargotracker.routing.domain.model.VoyageLeg;
import com.example.cargotracker.routing.domain.services.CargoTypeConstraint;
import com.example.cargotracker.routing.domain.services.CompositeRouteConstraintChecker;
import com.example.cargotracker.routing.domain.services.DeadlineConstraint;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("RouteConstraintChecker ドメインサービステスト")
class RouteConstraintCheckerTest {

    private static final RouteSearchQuery QUERY_GENERAL = new RouteSearchQuery(
        "JPTYO", "SGSIN", LocalDate.of(2026, 6, 20),
        CargoType.GENERAL, new BigDecimal("100")
    );

    private static final RouteSearchQuery QUERY_REFRIGERATED = new RouteSearchQuery(
        "JPTYO", "SGSIN", LocalDate.of(2026, 6, 20),
        CargoType.REFRIGERATED, new BigDecimal("100")
    );

    private static final Voyage VOYAGE_GENERAL = new Voyage(
        "SG001", "Pacific Lines",
        Set.of(CargoType.GENERAL, CargoType.REFRIGERATED),
        List.of(new VoyageLeg("JPTYO", "SGSIN",
            LocalDate.of(2026, 6, 1), LocalDate.of(2026, 6, 15)))
    );

    private static final Voyage VOYAGE_GENERAL_LATE = new Voyage(
        "SG003", "Pacific Lines",
        Set.of(CargoType.GENERAL),
        List.of(new VoyageLeg("JPTYO", "SGSIN",
            LocalDate.of(2026, 6, 10), LocalDate.of(2026, 6, 25)))
    );

    private static final Voyage VOYAGE_HAZARDOUS_ONLY = new Voyage(
        "SG002", "Korea Shipping",
        Set.of(CargoType.HAZARDOUS),
        List.of(new VoyageLeg("JPTYO", "SGSIN",
            LocalDate.of(2026, 6, 1), LocalDate.of(2026, 6, 15)))
    );

    // ── DeadlineConstraint ──────────────────────────────────────────────────────

    @Test
    @DisplayName("DeadlineConstraint: 期限内到着は合格")
    void deadline_満たす() {
        assertThat(new DeadlineConstraint().satisfies(VOYAGE_GENERAL, QUERY_GENERAL)).isTrue();
    }

    @Test
    @DisplayName("DeadlineConstraint: 期限超過は不合格")
    void deadline_超過() {
        assertThat(new DeadlineConstraint().satisfies(VOYAGE_GENERAL_LATE, QUERY_GENERAL)).isFalse();
    }

    @Test
    @DisplayName("DeadlineConstraint: 到着日 == 期限は合格")
    void deadline_ちょうど() {
        Voyage voyageExact = new Voyage("SG099", "Test",
            Set.of(CargoType.GENERAL),
            List.of(new VoyageLeg("JPTYO", "SGSIN",
                LocalDate.of(2026, 6, 1), LocalDate.of(2026, 6, 20))));
        assertThat(new DeadlineConstraint().satisfies(voyageExact, QUERY_GENERAL)).isTrue();
    }

    // ── CargoTypeConstraint ────────────────────────────────────────────────────

    @Test
    @DisplayName("CargoTypeConstraint: 対応貨物種別は合格")
    void cargoType_対応あり() {
        assertThat(new CargoTypeConstraint().satisfies(VOYAGE_GENERAL, QUERY_REFRIGERATED)).isTrue();
    }

    @Test
    @DisplayName("CargoTypeConstraint: 非対応貨物種別は不合格")
    void cargoType_非対応() {
        assertThat(new CargoTypeConstraint().satisfies(VOYAGE_HAZARDOUS_ONLY, QUERY_GENERAL)).isFalse();
    }

    // ── CompositeRouteConstraintChecker ────────────────────────────────────────

    @Test
    @DisplayName("CompositeRouteConstraintChecker: 全制約が合格なら合格")
    void composite_全合格() {
        var checker = new CompositeRouteConstraintChecker(
            new DeadlineConstraint(), new CargoTypeConstraint()
        );
        assertThat(checker.satisfies(VOYAGE_GENERAL, QUERY_GENERAL)).isTrue();
    }

    @Test
    @DisplayName("CompositeRouteConstraintChecker: 1 つでも不合格なら不合格")
    void composite_一部不合格() {
        var checker = new CompositeRouteConstraintChecker(
            new DeadlineConstraint(), new CargoTypeConstraint()
        );
        // VOYAGE_GENERAL_LATE は期限超過
        assertThat(checker.satisfies(VOYAGE_GENERAL_LATE, QUERY_GENERAL)).isFalse();
    }
}
