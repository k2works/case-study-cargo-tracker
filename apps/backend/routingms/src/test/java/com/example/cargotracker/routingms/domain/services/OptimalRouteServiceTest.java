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

import static org.assertj.core.api.Assertions.assertThat;

/**
 * US08 先行スパイク: OptimalRouteService の Dijkstra ベース PoC。
 *
 * 検証スコープ:
 * - 単一区間の経路候補算出
 * - 複数区間（中継港経由）の経路候補算出
 * - 到着期限による絞り込み
 * - 貨物種別（HAZARDOUS / REFRIGERATED）による絞り込み
 * - 寄港地連続性（前の到着港 == 次の出発港）の担保
 */
class OptimalRouteServiceTest {

    private OptimalRouteService service;

    // テスト用エッジ群（JPYOK → USLAX 直行、JPYOK → TWKHH → USLAX 経由）
    private List<TransitEdge> edges;

    @BeforeEach
    void setUp() {
        // 直行便: JPYOK → USLAX (V001)
        TransitEdge direct = new TransitEdge(
                "V001", "JPYOK", "USLAX",
                LocalDateTime.of(2099, 7, 1, 9, 0),
                LocalDateTime.of(2099, 7, 15, 18, 0),
                List.of("GENERAL", "REFRIGERATED"));

        // 経由便1: JPYOK → TWKHH (V002)
        TransitEdge leg1 = new TransitEdge(
                "V002", "JPYOK", "TWKHH",
                LocalDateTime.of(2099, 7, 2, 8, 0),
                LocalDateTime.of(2099, 7, 4, 12, 0),
                List.of("GENERAL", "HAZARDOUS"));

        // 経由便2: TWKHH → USLAX (V003)
        TransitEdge leg2 = new TransitEdge(
                "V003", "TWKHH", "USLAX",
                LocalDateTime.of(2099, 7, 5, 6, 0),
                LocalDateTime.of(2099, 7, 20, 9, 0),
                List.of("GENERAL", "HAZARDOUS"));

        // 別路線: JPYOK → SGSIN (V004, 到着期限超え)
        TransitEdge late = new TransitEdge(
                "V004", "JPYOK", "USLAX",
                LocalDateTime.of(2099, 8, 1, 9, 0),
                LocalDateTime.of(2099, 8, 31, 18, 0),
                List.of("GENERAL"));

        edges = List.of(direct, leg1, leg2, late);
        service = new OptimalRouteService(edges);
    }

    @Test
    void JPYOK_から_USLAX_への_直行経路を取得できる() {
        var spec = new RouteSearchSpecification(
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
        var spec = new RouteSearchSpecification(
                "JPYOK", "USLAX",
                LocalDate.of(2099, 7, 31),
                CargoType.GENERAL);

        List<TransitPath> candidates = service.findCandidates(spec);

        // V002 → V003 の 2 区間経路が含まれる
        assertThat(candidates).anyMatch(p ->
                p.edges().size() == 2
                && p.edges().get(0).voyageNumber().equals("V002")
                && p.edges().get(1).voyageNumber().equals("V003"));
    }

    @Test
    void 到着期限を超える経路は除外される() {
        // 到着期限 2099-07-16 では V004（到着 2099-08-31）は除外される
        var spec = new RouteSearchSpecification(
                "JPYOK", "USLAX",
                LocalDate.of(2099, 7, 16),
                CargoType.GENERAL);

        List<TransitPath> candidates = service.findCandidates(spec);

        // V004 のみの候補は存在しない
        assertThat(candidates).noneMatch(p ->
                p.edges().stream().anyMatch(e -> e.voyageNumber().equals("V004")));
    }

    @Test
    void HAZARDOUS貨物は対応可能な航海のみが候補になる() {
        var spec = new RouteSearchSpecification(
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
        var spec = new RouteSearchSpecification(
                "JPYOK", "USLAX",
                LocalDate.of(2099, 7, 31),
                CargoType.GENERAL);

        List<TransitPath> candidates = service.findCandidates(spec);

        // 全ての経路で到着港と次の出発港が一致する
        for (TransitPath path : candidates) {
            List<TransitEdge> pathEdges = path.edges();
            for (int i = 0; i < pathEdges.size() - 1; i++) {
                assertThat(pathEdges.get(i).toUnLocode())
                        .isEqualTo(pathEdges.get(i + 1).fromUnLocode());
            }
        }
    }

    @Test
    void 経路の各区間で乗り継ぎ時間が確保されている() {
        var spec = new RouteSearchSpecification(
                "JPYOK", "USLAX",
                LocalDate.of(2099, 7, 31),
                CargoType.GENERAL);

        List<TransitPath> candidates = service.findCandidates(spec);

        // 前区間の到着時刻が次区間の出発時刻より前である
        for (TransitPath path : candidates) {
            List<TransitEdge> pathEdges = path.edges();
            for (int i = 0; i < pathEdges.size() - 1; i++) {
                assertThat(pathEdges.get(i).arrivalTime())
                        .isBefore(pathEdges.get(i + 1).departureTime());
            }
        }
    }
}
