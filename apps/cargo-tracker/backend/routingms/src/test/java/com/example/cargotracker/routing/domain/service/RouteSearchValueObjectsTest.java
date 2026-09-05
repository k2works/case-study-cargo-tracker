package com.example.cargotracker.routing.domain.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.cargotracker.routing.domain.model.valueobjects.CargoType;
import com.example.cargotracker.routing.domain.model.valueobjects.RouteSearchSpecification;
import com.example.cargotracker.routing.domain.model.valueobjects.TransitEdge;
import com.example.cargotracker.routing.domain.model.valueobjects.TransitPath;
import com.example.cargotracker.shared.domain.error.BusinessRuleViolation;
import com.example.cargotracker.shared.domain.location.Location;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** 経路探索の値オブジェクト（US08）。 */
class RouteSearchValueObjectsTest {

    /** 業務タイムゾーン。期限は日付なので、どの時間帯で日付にするかが結果を変える。 */
    private static final ZoneId ZONE = ZoneId.of("Asia/Tokyo");

    private static final Instant DEPART = Instant.parse("2026-09-10T00:00:00Z");
    private static final Instant ARRIVE = Instant.parse("2026-09-24T09:00:00Z");

    private static TransitEdge edge(String from, String to, Instant load, Instant unload) {
        return new TransitEdge("V-MOL-001", Location.of(from), Location.of(to), load, unload);
    }

    private static RouteSearchSpecification spec(LocalDate deadline) {
        return new RouteSearchSpecification(Location.of("JPTYO"), Location.of("USNYC"),
                deadline, CargoType.GENERAL, Set.of(), null);
    }

    @Test
    @DisplayName("区間は到着が出発より後でなければならない")
    void edgeMustArriveAfterDeparture() {
        assertThatThrownBy(() -> edge("JPTYO", "USNYC", ARRIVE, DEPART))
                .isInstanceOf(BusinessRuleViolation.class);
    }

    @Test
    @DisplayName("同じ港へ向かう区間は作れない")
    void edgeMustConnectDifferentPorts() {
        assertThatThrownBy(() -> edge("JPTYO", "JPTYO", DEPART, ARRIVE))
                .isInstanceOf(BusinessRuleViolation.class);
    }

    @Test
    @DisplayName("経路は区間が連結し、時刻が昇順でなければならない")
    void pathMustBeConnectedAndOrdered() {
        assertThatThrownBy(() -> new TransitPath(List.of(
                edge("JPTYO", "SGSIN", DEPART, Instant.parse("2026-09-16T00:00:00Z")),
                // 前の区間の到着地（SGSIN）から続いていない。
                edge("NLRTM", "USNYC", Instant.parse("2026-09-17T00:00:00Z"), ARRIVE))))
                .isInstanceOf(BusinessRuleViolation.class)
                .hasMessageContaining("連結");

        assertThatThrownBy(() -> new TransitPath(List.of(
                edge("JPTYO", "SGSIN", DEPART, Instant.parse("2026-09-16T00:00:00Z")),
                // 前の区間の到着より前に出る便には乗れない。
                edge("SGSIN", "USNYC", Instant.parse("2026-09-15T00:00:00Z"), ARRIVE))))
                .isInstanceOf(BusinessRuleViolation.class);
    }

    @Test
    @DisplayName("空の経路は作れない")
    void pathMustHaveAtLeastOneEdge() {
        assertThatThrownBy(() -> new TransitPath(List.of()))
                .isInstanceOf(BusinessRuleViolation.class);
    }

    @Test
    @DisplayName("所要時間は最初の出発から最後の到着まで（乗り継ぎの待ちも含む）")
    void totalDurationSpansTheWholeJourney() {
        TransitPath path = new TransitPath(List.of(
                edge("JPTYO", "SGSIN", DEPART, Instant.parse("2026-09-16T00:00:00Z")),
                edge("SGSIN", "USNYC", Instant.parse("2026-09-18T00:00:00Z"), ARRIVE)));

        // 港で 2 日待つあいだも荷主は待っている。区間の合計にすると待ちが消える。
        assertThat(path.totalDuration()).isEqualTo(Duration.between(DEPART, ARRIVE));
        assertThat(path.origin()).isEqualTo(Location.of("JPTYO"));
        assertThat(path.destination()).isEqualTo(Location.of("USNYC"));
        assertThat(path.isDirect()).isFalse();
        assertThat(path.viaPorts()).containsExactly(Location.of("SGSIN"));
    }

    @Test
    @DisplayName("区間が 1 本なら直行便")
    void singleEdgeIsDirect() {
        TransitPath path = new TransitPath(List.of(edge("JPTYO", "USNYC", DEPART, ARRIVE)));

        assertThat(path.isDirect()).isTrue();
        assertThat(path.viaPorts()).isEmpty();
    }

    @Test
    @DisplayName("期限は日付で比べる。期限当日の到着は間に合う")
    void deadlineIsComparedByDate() {
        TransitPath path = new TransitPath(List.of(edge("JPTYO", "USNYC", DEPART, ARRIVE)));
        // ARRIVE は業務タイムゾーンでは 2026-09-24 18:00。
        LocalDate sameDay = LocalDate.of(2026, 9, 24);

        assertThat(path.overdueDays(spec(sameDay), ZONE)).isZero();
        assertThat(path.meetsDeadline(spec(sameDay), ZONE)).isTrue();
        assertThat(path.meetsDeadline(spec(sameDay.minusDays(1)), ZONE)).isFalse();
        assertThat(path.overdueDays(spec(sameDay.minusDays(2)), ZONE)).isEqualTo(2);
    }

    @Test
    @DisplayName("探索条件は端点が同じでは成り立たない")
    void specificationRejectsSameEndpoints() {
        assertThatThrownBy(() -> new RouteSearchSpecification(Location.of("JPTYO"),
                Location.of("JPTYO"), LocalDate.of(2026, 12, 1), CargoType.GENERAL,
                Set.of(), null))
                .isInstanceOf(BusinessRuleViolation.class);
    }

    @Test
    @DisplayName("探索の起点は departFrom があればそれ（誤配の再設計・IT11 で使う）")
    void searchStartsFromDepartFromWhenGiven() {
        RouteSearchSpecification fromSingapore = new RouteSearchSpecification(
                Location.of("JPTYO"), Location.of("USNYC"), LocalDate.of(2026, 12, 1),
                CargoType.GENERAL, Set.of(), Location.of("SGSIN"));

        assertThat(fromSingapore.searchOrigin()).isEqualTo(Location.of("SGSIN"));
        assertThat(spec(LocalDate.of(2026, 12, 1)).searchOrigin())
                .isEqualTo(Location.of("JPTYO"));
    }

    @Test
    @DisplayName("除外港は経路の途中にも端点にも使えない")
    void excludedPortsAreRejectedAsEndpoints() {
        assertThatThrownBy(() -> new RouteSearchSpecification(Location.of("JPTYO"),
                Location.of("USNYC"), LocalDate.of(2026, 12, 1), CargoType.GENERAL,
                Set.of(Location.of("USNYC")), null))
                .isInstanceOf(BusinessRuleViolation.class);
    }
}
