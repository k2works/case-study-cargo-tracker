package com.example.cargotracker.routing.domain.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.cargotracker.routing.domain.model.valueobjects.CargoType;
import com.example.cargotracker.routing.domain.model.valueobjects.RouteSearchSpecification;
import com.example.cargotracker.routing.domain.model.valueobjects.TransitEdge;
import com.example.cargotracker.routing.domain.model.valueobjects.TransitPath;
import com.example.cargotracker.shared.domain.location.Location;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 経路探索（US08）。集約境界を越えるのでドメインサービスに置く。
 *
 * <p>グラフはテストが直接組む。投影から組む部分（{@code VoyageGraph} の作り方）は
 * 実 DB の統合テストで見る。探索の判断はここだけで決まる。</p>
 */
class RouteSearchServiceTest {

    private static final ZoneId ZONE = ZoneId.of("Asia/Tokyo");
    private static final LocalDate DEADLINE = LocalDate.of(2026, 10, 31);

    private final RouteSearchService service = new RouteSearchService(ZONE);

    /** テスト用のグラフ。港ごとの出る便を持つだけ。 */
    private static final class Graph implements VoyageGraph {
        private final Map<Location, List<TransitEdge>> edges = new LinkedHashMap<>();
        private final Map<String, Set<CargoType>> accepted = new LinkedHashMap<>();

        Graph add(String voyage, String from, String to, String load, String unload,
                CargoType... types) {
            TransitEdge edge = new TransitEdge(voyage, Location.of(from), Location.of(to),
                    Instant.parse(load), Instant.parse(unload));
            edges.computeIfAbsent(edge.load(), k -> new ArrayList<>()).add(edge);
            accepted.put(voyage, Set.of(types.length == 0 ? new CargoType[] {CargoType.GENERAL}
                    : types));
            return this;
        }

        @Override
        public List<TransitEdge> edgesFrom(Location location) {
            return edges.getOrDefault(location, List.of());
        }

        @Override
        public Set<CargoType> acceptedCargoTypes(String voyageNumber) {
            return accepted.getOrDefault(voyageNumber, Set.of());
        }
    }

    private static RouteSearchSpecification spec(CargoType type, Set<Location> exclude) {
        return new RouteSearchSpecification(Location.of("JPTYO"), Location.of("USNYC"),
                DEADLINE, type, exclude, null);
    }

    private static List<String> voyagesOf(TransitPath path) {
        return path.edges().stream().map(TransitEdge::voyageNumber).toList();
    }

    @Test
    @DisplayName("受入基準 1・2: 寄港地の接続をたどって候補が出る")
    void findsConnectingRoutes() {
        Graph graph = new Graph()
                .add("V-1", "JPTYO", "SGSIN", "2026-09-10T00:00:00Z", "2026-09-16T00:00:00Z")
                .add("V-2", "SGSIN", "USNYC", "2026-09-18T00:00:00Z", "2026-10-01T00:00:00Z");

        List<TransitPath> found = service.findCandidates(spec(CargoType.GENERAL, Set.of()), graph);

        assertThat(found).hasSize(1);
        assertThat(voyagesOf(found.get(0))).containsExactly("V-1", "V-2");
    }

    @Test
    @DisplayName("受入基準 5: 直行便は最優先（所要が長くても先に出す）")
    void directRouteComesFirst() {
        Graph graph = new Graph()
                // 直行だが遅い。
                .add("V-DIRECT", "JPTYO", "USNYC", "2026-09-10T00:00:00Z",
                        "2026-10-05T00:00:00Z")
                // 乗り継ぎだが速い。
                .add("V-1", "JPTYO", "SGSIN", "2026-09-10T00:00:00Z", "2026-09-16T00:00:00Z")
                .add("V-2", "SGSIN", "USNYC", "2026-09-17T00:00:00Z", "2026-09-25T00:00:00Z");

        List<TransitPath> found = service.findCandidates(spec(CargoType.GENERAL, Set.of()), graph);

        assertThat(found).hasSize(2);
        assertThat(found.get(0).isDirect()).isTrue();
        assertThat(voyagesOf(found.get(1))).containsExactly("V-1", "V-2");
    }

    @Test
    @DisplayName("受入基準 4: 乗り継ぎ同士は所要の短い順")
    void ordersByDuration() {
        Graph graph = new Graph()
                .add("V-1", "JPTYO", "SGSIN", "2026-09-10T00:00:00Z", "2026-09-16T00:00:00Z")
                .add("V-2", "SGSIN", "USNYC", "2026-09-17T00:00:00Z", "2026-09-25T00:00:00Z")
                .add("V-3", "JPTYO", "NLRTM", "2026-09-10T00:00:00Z", "2026-09-20T00:00:00Z")
                .add("V-4", "NLRTM", "USNYC", "2026-09-21T00:00:00Z", "2026-09-30T00:00:00Z");

        List<TransitPath> found = service.findCandidates(spec(CargoType.GENERAL, Set.of()), graph);

        assertThat(found).hasSize(2);
        assertThat(voyagesOf(found.get(0))).containsExactly("V-1", "V-2");
    }

    @Test
    @DisplayName("受入基準 3 の前提: 対応しない貨物種別の航海は通らない")
    void skipsVoyagesThatRejectTheCargoType() {
        Graph graph = new Graph()
                .add("V-GENERAL", "JPTYO", "USNYC", "2026-09-10T00:00:00Z",
                        "2026-09-25T00:00:00Z", CargoType.GENERAL)
                .add("V-HAZ", "JPTYO", "USNYC", "2026-09-11T00:00:00Z",
                        "2026-09-26T00:00:00Z", CargoType.GENERAL, CargoType.HAZARDOUS);

        List<TransitPath> found =
                service.findCandidates(spec(CargoType.HAZARDOUS, Set.of()), graph);

        assertThat(found).hasSize(1);
        assertThat(voyagesOf(found.get(0))).containsExactly("V-HAZ");
    }

    @Test
    @DisplayName("除外した港は通らない（US10 の条件調整で使う）")
    void skipsExcludedPorts() {
        Graph graph = new Graph()
                .add("V-1", "JPTYO", "SGSIN", "2026-09-10T00:00:00Z", "2026-09-16T00:00:00Z")
                .add("V-2", "SGSIN", "USNYC", "2026-09-17T00:00:00Z", "2026-09-25T00:00:00Z");

        assertThat(service.findCandidates(
                spec(CargoType.GENERAL, Set.of(Location.of("SGSIN"))), graph)).isEmpty();
    }

    @Test
    @DisplayName("受入基準 6: 期限内に着けない経路は候補にしない（例外にしない）")
    void excludesRoutesThatMissTheDeadline() {
        Graph graph = new Graph()
                .add("V-LATE", "JPTYO", "USNYC", "2026-09-10T00:00:00Z",
                        "2026-11-30T00:00:00Z");

        // 空リストで返す。エラーにすると「探索が壊れた」と読める。
        assertThat(service.findCandidates(spec(CargoType.GENERAL, Set.of()), graph)).isEmpty();
    }

    @Test
    @DisplayName("期限当日に着く便は候補に残る（日付で比べる）")
    void keepsRoutesArrivingOnTheDeadline() {
        Graph graph = new Graph()
                // 業務タイムゾーンで 2026-10-31 09:00 に着く。
                .add("V-ONTIME", "JPTYO", "USNYC", "2026-09-10T00:00:00Z",
                        "2026-10-31T00:00:00Z");

        assertThat(service.findCandidates(spec(CargoType.GENERAL, Set.of()), graph)).hasSize(1);
    }

    @Test
    @DisplayName("同じ港を 2 度通る経路は作らない")
    void doesNotRevisitPorts() {
        Graph graph = new Graph()
                .add("V-1", "JPTYO", "SGSIN", "2026-09-10T00:00:00Z", "2026-09-12T00:00:00Z")
                .add("V-2", "SGSIN", "JPTYO", "2026-09-13T00:00:00Z", "2026-09-15T00:00:00Z")
                .add("V-3", "SGSIN", "USNYC", "2026-09-16T00:00:00Z", "2026-09-25T00:00:00Z");

        List<TransitPath> found = service.findCandidates(spec(CargoType.GENERAL, Set.of()), graph);

        assertThat(found).hasSize(1);
        assertThat(voyagesOf(found.get(0))).containsExactly("V-1", "V-3");
    }

    @Test
    @DisplayName("乗り継ぎの上限を超える経路は探さない（ADR-0007 の打ち切り）")
    void stopsAtTheTransferLimit() {
        // JPTYO → A → B → C → USNYC は 4 区間（乗り継ぎ 3 回）。上限ちょうど。
        Graph graph = new Graph()
                .add("V-1", "JPTYO", "SGSIN", "2026-09-10T00:00:00Z", "2026-09-12T00:00:00Z")
                .add("V-2", "SGSIN", "NLRTM", "2026-09-13T00:00:00Z", "2026-09-15T00:00:00Z")
                .add("V-3", "NLRTM", "GBLON", "2026-09-16T00:00:00Z", "2026-09-18T00:00:00Z")
                .add("V-4", "GBLON", "USNYC", "2026-09-19T00:00:00Z", "2026-09-25T00:00:00Z");

        assertThat(service.findCandidates(spec(CargoType.GENERAL, Set.of()), graph)).hasSize(1);

        // もう 1 区間必要な経路は探さない。無制限だと航海が増えたときに応答が返らない。
        Graph deeper = new Graph()
                .add("V-1", "JPTYO", "SGSIN", "2026-09-10T00:00:00Z", "2026-09-12T00:00:00Z")
                .add("V-2", "SGSIN", "NLRTM", "2026-09-13T00:00:00Z", "2026-09-15T00:00:00Z")
                .add("V-3", "NLRTM", "GBLON", "2026-09-16T00:00:00Z", "2026-09-18T00:00:00Z")
                .add("V-4", "GBLON", "DEHAM", "2026-09-19T00:00:00Z", "2026-09-21T00:00:00Z")
                .add("V-5", "DEHAM", "USNYC", "2026-09-22T00:00:00Z", "2026-09-28T00:00:00Z");

        assertThat(service.findCandidates(spec(CargoType.GENERAL, Set.of()), deeper)).isEmpty();
    }

    @Test
    @DisplayName("誤配の再設計は現在地から探す（departFrom）")
    void searchesFromDepartFromWhenGiven() {
        Graph graph = new Graph()
                .add("V-1", "JPTYO", "SGSIN", "2026-09-10T00:00:00Z", "2026-09-16T00:00:00Z")
                .add("V-2", "SGSIN", "USNYC", "2026-09-18T00:00:00Z", "2026-10-01T00:00:00Z");

        List<TransitPath> found = service.findCandidates(
                new RouteSearchSpecification(Location.of("JPTYO"), Location.of("USNYC"),
                        DEADLINE, CargoType.GENERAL, Set.of(), Location.of("SGSIN")),
                graph);

        assertThat(found).hasSize(1);
        assertThat(voyagesOf(found.get(0))).containsExactly("V-2");
    }

    @Test
    @DisplayName("ADR-0007: 打ち切りに当たったことは候補 0 件と区別できる")
    void reportsTruncationSeparatelyFromEmptyResult() {
        // 乗り継ぎの上限を超える経路しか無い。件数だけで判断すると「候補が無い」と
        // 同じ見え方になり、条件を変えても直らないものを変え続けさせる。
        Graph tooDeep = new Graph()
                .add("V-1", "JPTYO", "SGSIN", "2026-09-10T00:00:00Z", "2026-09-12T00:00:00Z")
                .add("V-2", "SGSIN", "NLRTM", "2026-09-13T00:00:00Z", "2026-09-15T00:00:00Z")
                .add("V-3", "NLRTM", "GBLON", "2026-09-16T00:00:00Z", "2026-09-18T00:00:00Z")
                .add("V-4", "GBLON", "DEHAM", "2026-09-19T00:00:00Z", "2026-09-21T00:00:00Z")
                .add("V-5", "DEHAM", "USNYC", "2026-09-22T00:00:00Z", "2026-09-28T00:00:00Z");

        RouteSearchService.RouteSearchResult truncated =
                service.search(spec(CargoType.GENERAL, Set.of()), tooDeep);
        assertThat(truncated.candidates()).isEmpty();
        assertThat(truncated.truncated()).isTrue();

        // 本当に便が無いときは打ち切りではない。
        RouteSearchService.RouteSearchResult empty =
                service.search(spec(CargoType.GENERAL, Set.of()), new Graph());
        assertThat(empty.candidates()).isEmpty();
        assertThat(empty.truncated()).isFalse();
    }
}
