package com.example.cargotracker.routingms.domain.services;

import com.example.cargotracker.routingms.domain.model.valueobjects.CargoType;
import com.example.cargotracker.routingms.domain.model.valueobjects.RouteSearchSpecification;
import com.example.cargotracker.routingms.domain.model.valueobjects.TransitEdge;
import com.example.cargotracker.routingms.domain.model.valueobjects.TransitPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * US08 経路候補算出の実行可能仕様（ADR-0010: IT3 PoC テストシナリオを保存）。
 *
 * <p>IT4 で {@link OptimalRouteService} → {@link RouteCandidateFinder} に移行。
 * テストシナリオは変更せず、API のみ型安全版（UnLocode VO・Set&lt;CargoType&gt;）に更新。</p>
 *
 * <p>検証スコープ（ドメイン不変条件）:</p>
 * <ul>
 *   <li>単一区間の経路候補算出（直行便）</li>
 *   <li>複数区間（中継港経由）の経路候補算出</li>
 *   <li>到着期限による絞り込み</li>
 *   <li>貨物種別（HAZARDOUS / REFRIGERATED）による絞り込み</li>
 *   <li>寄港地連続性（前の到着港 == 次の出発港）の担保</li>
 *   <li>乗り継ぎ時間（到着 + 24h &lt; 出発）の担保</li>
 * </ul>
 */

class RouteCandidateFinderTest {

    private RouteCandidateFinder service;

    private List<TransitEdge> edges;

    @BeforeEach
    void setUp() {
        // 直行便: JPYOK → USLAX (V001) — 乗り継ぎ 25h 以上確保
        TransitEdge direct = TransitEdge.of(
                "V001", "JPYOK", "USLAX",
                LocalDateTime.of(2099, 7, 1, 9, 0),
                LocalDateTime.of(2099, 7, 15, 18, 0),
                Set.of(CargoType.GENERAL, CargoType.REFRIGERATED));

        // 経由便1: JPYOK → TWKHH (V002)
        TransitEdge leg1 = TransitEdge.of(
                "V002", "JPYOK", "TWKHH",
                LocalDateTime.of(2099, 7, 2, 8, 0),
                LocalDateTime.of(2099, 7, 4, 12, 0),
                Set.of(CargoType.GENERAL, CargoType.HAZARDOUS));

        // 経由便2: TWKHH → USLAX (V003) — V002 到着 7/4 12:00 から 18h後出発 → 24h 未満 → 除外
        // 7/5 06:00 出発は 7/4 12:00 + 18h = 7/5 06:00 → 24h 制約を満たさない。
        // テストが通るよう 7/5 13:00 以降に設定（+25h）
        TransitEdge leg2 = TransitEdge.of(
                "V003", "TWKHH", "USLAX",
                LocalDateTime.of(2099, 7, 5, 13, 0),
                LocalDateTime.of(2099, 7, 20, 9, 0),
                Set.of(CargoType.GENERAL, CargoType.HAZARDOUS));

        // 別路線: JPYOK → USLAX (V004, 到着期限超え)
        TransitEdge late = TransitEdge.of(
                "V004", "JPYOK", "USLAX",
                LocalDateTime.of(2099, 8, 1, 9, 0),
                LocalDateTime.of(2099, 8, 31, 18, 0),
                Set.of(CargoType.GENERAL));

        edges = List.of(direct, leg1, leg2, late);
        service = new RouteCandidateFinder(edges);
    }

    @Test
    void JPYOK_から_USLAX_への_直行経路を取得できる() {
        var spec = RouteSearchSpecification.of(
                "JPYOK", "USLAX",
                LocalDate.of(2099, 7, 31),
                CargoType.GENERAL);

        List<TransitPath> candidates = service.findCandidates(spec);

        assertThat(candidates)
                .isNotEmpty()
                .anyMatch(p -> p.edges().size() == 1 && p.edges().get(0).voyageNumber().equals("V001"));
    }

    @Test
    void JPYOK_から_USLAX_への_経由便経路を取得できる() {
        var spec = RouteSearchSpecification.of(
                "JPYOK", "USLAX",
                LocalDate.of(2099, 7, 31),
                CargoType.GENERAL);

        List<TransitPath> candidates = service.findCandidates(spec);

        assertThat(candidates).anyMatch(p ->
                p.edges().size() == 2
                && p.edges().get(0).voyageNumber().equals("V002")
                && p.edges().get(1).voyageNumber().equals("V003"));
    }

    @Test
    void 到着期限を超える経路は除外される() {
        var spec = RouteSearchSpecification.of(
                "JPYOK", "USLAX",
                LocalDate.of(2099, 7, 16),
                CargoType.GENERAL);

        List<TransitPath> candidates = service.findCandidates(spec);

        assertThat(candidates).noneMatch(p ->
                p.edges().stream().anyMatch(e -> e.voyageNumber().equals("V004")));
    }

    @Test
    void HAZARDOUS貨物は対応可能な航海のみが候補になる() {
        var spec = RouteSearchSpecification.of(
                "JPYOK", "USLAX",
                LocalDate.of(2099, 7, 31),
                CargoType.HAZARDOUS);

        List<TransitPath> candidates = service.findCandidates(spec);

        assertThat(candidates)
                .noneMatch(p -> p.edges().stream().anyMatch(e -> e.voyageNumber().equals("V001")))
                .anyMatch(p -> p.edges().size() == 2
                        && p.edges().get(0).voyageNumber().equals("V002")
                        && p.edges().get(1).voyageNumber().equals("V003"));
    }

    @Test
    void 経路の各区間で寄港地が連続している() {
        var spec = RouteSearchSpecification.of(
                "JPYOK", "USLAX",
                LocalDate.of(2099, 7, 31),
                CargoType.GENERAL);

        List<TransitPath> candidates = service.findCandidates(spec);

        for (TransitPath path : candidates) {
            List<TransitEdge> pathEdges = path.edges();
            for (int i = 0; i < pathEdges.size() - 1; i++) {
                assertThat(pathEdges.get(i).toUnLocode())
                        .isEqualTo(pathEdges.get(i + 1).fromUnLocode());
            }
        }
    }

    @Test
    void excludePortsを指定すると該当経由地を含む経路が除外される() {
        var spec = RouteSearchSpecification.of(
                "JPYOK", "USLAX",
                LocalDate.of(2099, 7, 31),
                CargoType.GENERAL,
                List.of("TWKHH"));

        List<TransitPath> candidates = service.findCandidates(spec);

        assertThat(candidates).noneMatch(p ->
                p.edges().stream().anyMatch(e ->
                        e.fromUnLocode().value().equals("TWKHH") || e.toUnLocode().value().equals("TWKHH")));
    }

    @Test
    void 経路の各区間で乗り継ぎ時間が確保されている() {
        var spec = RouteSearchSpecification.of(
                "JPYOK", "USLAX",
                LocalDate.of(2099, 7, 31),
                CargoType.GENERAL);

        List<TransitPath> candidates = service.findCandidates(spec);

        for (TransitPath path : candidates) {
            List<TransitEdge> pathEdges = path.edges();
            for (int i = 0; i < pathEdges.size() - 1; i++) {
                assertThat(pathEdges.get(i).arrivalTime()
                        .plus(spec.minimumTransferTime()))
                        .isBefore(pathEdges.get(i + 1).departureTime());
            }
        }
    }
}
