package com.example.cargotracker.routingms.domain.services;

import com.example.cargotracker.routingms.domain.model.valueobjects.RouteSearchSpecification;
import com.example.cargotracker.routingms.domain.model.valueobjects.TransitEdge;
import com.example.cargotracker.routingms.domain.model.valueobjects.TransitPath;

import java.util.ArrayList;
import java.util.List;

/**
 * US08 先行スパイク: DFS（深さ優先全経路列挙）による経路候補算出サービス（PoC）。
 *
 * <p>ポートをノード、{@link TransitEdge} を有向エッジとしてグラフ探索を行う。
 * 実装は再帰的 DFS で全候補を列挙する。BFS / Dijkstra ではない。</p>
 *
 * <p><b>PoC の制約と IT4 への引き継ぎ事項</b>:
 * <ul>
 *   <li>グラフ表現: {@code edges} 全件走査（O(|E|^d)）。IT4 では隣接リスト
 *       {@code Map<String, List<TransitEdge>>} に変更すること（ADR-0010 参照）。</li>
 *   <li>型安全性: {@code fromUnLocode} 等は {@code String} で管理。IT4 で {@code UnLocode} 値オブジェクトへ置換予定。</li>
 *   <li>乗り継ぎ最小時間: 現状は「到着 &lt; 出発」のみ検証（1 分乗り継ぎも許容）。IT4 で 24h 制約を追加すること。</li>
 *   <li>候補 0 件時: 空リストを返却するのみ。IT4 で代替案提示を仕様化すること。</li>
 *   <li>本クラスの「捨てる / プロモート」方針は ADR-0010 を参照。</li>
 * </ul>
 * </p>
 */
public class OptimalRouteService {

    private final List<TransitEdge> edges;

    public OptimalRouteService(List<TransitEdge> edges) {
        this.edges = edges;
    }

    public List<TransitPath> findCandidates(RouteSearchSpecification spec) {
        List<TransitPath> results = new ArrayList<>();
        dfs(spec, spec.origin(), new ArrayList<>(), results);
        return results;
    }

    private void dfs(RouteSearchSpecification spec, String current,
                     List<TransitEdge> currentPath, List<TransitPath> results) {
        for (TransitEdge edge : edges) {
            if (isEligible(edge, spec, current, currentPath)) {
                explore(edge, spec, currentPath, results);
            }
        }
    }

    private boolean isEligible(TransitEdge edge, RouteSearchSpecification spec,
                                String current, List<TransitEdge> currentPath) {
        return edge.fromUnLocode().equals(current)
                && acceptsCargo(edge, spec)
                && hasValidTransfer(currentPath, edge);
    }

    private void explore(TransitEdge edge, RouteSearchSpecification spec,
                         List<TransitEdge> currentPath, List<TransitPath> results) {
        List<TransitEdge> newPath = new ArrayList<>(currentPath);
        newPath.add(edge);

        if (edge.toUnLocode().equals(spec.destination())) {
            if (!edge.arrivalTime().toLocalDate().isAfter(spec.arrivalDeadline())) {
                results.add(new TransitPath(newPath));
            }
        } else if (!isVisited(edge.toUnLocode(), currentPath)) {
            dfs(spec, edge.toUnLocode(), newPath, results);
        }
    }

    private boolean isVisited(String node, List<TransitEdge> path) {
        return path.stream()
                .anyMatch(e -> e.fromUnLocode().equals(node) || e.toUnLocode().equals(node));
    }

    private boolean acceptsCargo(TransitEdge edge, RouteSearchSpecification spec) {
        return edge.acceptedCargoTypes().contains(spec.cargoType().name());
    }

    private boolean hasValidTransfer(List<TransitEdge> path, TransitEdge next) {
        if (path.isEmpty()) {
            return true;
        }
        TransitEdge last = path.get(path.size() - 1);
        return last.arrivalTime().isBefore(next.departureTime());
    }
}
