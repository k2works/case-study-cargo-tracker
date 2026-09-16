package com.example.cargotracker.booking.domain.model.valueobjects;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.cargotracker.booking.application.port.RouteSearchRequest;
import com.example.cargotracker.shared.domain.error.BusinessRuleViolation;
import com.example.cargotracker.shared.domain.location.Location;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 経路まわりの値オブジェクトの不変条件（US08・US09）。
 *
 * <p>断る側を確かめないと、通す側だけが緑になる。壊れた候補は集約まで届いてから
 * 落ちるので、どこで壊れたのかが分からなくなる。</p>
 */
class RouteValueObjectsTest {

    private static final Instant LOAD = Instant.parse("2026-09-10T09:00:00Z");
    private static final Instant UNLOAD = Instant.parse("2026-09-24T18:00:00Z");

    private static Leg leg() {
        return new Leg("V-001", Location.of("JPTYO"), Location.of("USNYC"), LOAD, UNLOAD);
    }

    @Test
    @DisplayName("区間は航海番号・積地揚地・日時が揃っていないと作れない")
    void legRequiresEveryField() {
        assertThatThrownBy(() ->
                new Leg("  ", Location.of("JPTYO"), Location.of("USNYC"), LOAD, UNLOAD))
                .isInstanceOf(BusinessRuleViolation.class).hasMessageContaining("航海番号");
        assertThatThrownBy(() -> new Leg("V-001", null, Location.of("USNYC"), LOAD, UNLOAD))
                .isInstanceOf(BusinessRuleViolation.class).hasMessageContaining("積地と揚地");
        assertThatThrownBy(() -> new Leg("V-001", Location.of("JPTYO"), null, LOAD, UNLOAD))
                .isInstanceOf(BusinessRuleViolation.class).hasMessageContaining("積地と揚地");
        assertThatThrownBy(() ->
                new Leg("V-001", Location.of("JPTYO"), Location.of("USNYC"), null, UNLOAD))
                .isInstanceOf(BusinessRuleViolation.class).hasMessageContaining("積込日時");
        assertThatThrownBy(() ->
                new Leg("V-001", Location.of("JPTYO"), Location.of("USNYC"), LOAD, null))
                .isInstanceOf(BusinessRuleViolation.class).hasMessageContaining("積込日時");
    }

    @Test
    @DisplayName("積地と揚地が同じ区間は作れない")
    void legCannotStartAndEndAtTheSamePort() {
        assertThatThrownBy(() ->
                new Leg("V-001", Location.of("JPTYO"), Location.of("JPTYO"), LOAD, UNLOAD))
                .isInstanceOf(BusinessRuleViolation.class).hasMessageContaining("JPTYO");
    }

    @Test
    @DisplayName("荷揚が積込より後でない区間は作れない")
    void legMustUnloadAfterLoading() {
        assertThatThrownBy(() ->
                new Leg("V-001", Location.of("JPTYO"), Location.of("USNYC"), UNLOAD, LOAD))
                .isInstanceOf(BusinessRuleViolation.class);
        // 同時刻も後ではない。
        assertThatThrownBy(() ->
                new Leg("V-001", Location.of("JPTYO"), Location.of("USNYC"), LOAD, LOAD))
                .isInstanceOf(BusinessRuleViolation.class);
    }

    @Test
    @DisplayName("区間の無い経路候補は作れない")
    void routeCandidateNeedsAtLeastOneLeg() {
        assertThatThrownBy(() -> new RouteCandidate(List.of(), 0, true))
                .isInstanceOf(BusinessRuleViolation.class).hasMessageContaining("1 区間以上");
        assertThatThrownBy(() -> new RouteCandidate(null, 0, true))
                .isInstanceOf(BusinessRuleViolation.class);
        assertThatCode(() -> new RouteCandidate(List.of(leg()), 14, true))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("探索の条件は端点・期限・貨物種別が揃っていないと作れない")
    void routeSearchRequestRequiresEveryCondition() {
        assertThatThrownBy(() -> new RouteSearchRequest(null, Location.of("USNYC"),
                LocalDate.of(2026, 12, 1), CargoType.GENERAL, List.of(), null))
                .isInstanceOf(BusinessRuleViolation.class).hasMessageContaining("出発地と目的地");
        assertThatThrownBy(() -> new RouteSearchRequest(Location.of("JPTYO"), null,
                LocalDate.of(2026, 12, 1), CargoType.GENERAL, List.of(), null))
                .isInstanceOf(BusinessRuleViolation.class).hasMessageContaining("出発地と目的地");
        assertThatThrownBy(() -> new RouteSearchRequest(Location.of("JPTYO"),
                Location.of("JPTYO"), LocalDate.of(2026, 12, 1),
                CargoType.GENERAL, List.of(), null))
                .isInstanceOf(BusinessRuleViolation.class);
        assertThatThrownBy(() -> new RouteSearchRequest(Location.of("JPTYO"),
                Location.of("USNYC"), null, CargoType.GENERAL, List.of(), null))
                .isInstanceOf(BusinessRuleViolation.class).hasMessageContaining("到着期限");
        assertThatThrownBy(() -> new RouteSearchRequest(Location.of("JPTYO"),
                Location.of("USNYC"), LocalDate.of(2026, 12, 1), null, List.of(), null))
                .isInstanceOf(BusinessRuleViolation.class).hasMessageContaining("貨物種別");
    }
}
