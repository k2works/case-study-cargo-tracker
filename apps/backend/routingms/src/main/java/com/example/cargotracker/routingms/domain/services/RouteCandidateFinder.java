package com.example.cargotracker.routingms.domain.services;

import com.example.cargotracker.routingms.domain.model.valueobjects.RouteSearchSpecification;
import com.example.cargotracker.routingms.domain.model.valueobjects.TransitEdge;
import com.example.cargotracker.routingms.domain.model.valueobjects.TransitPath;
import com.example.cargotracker.routingms.domain.model.valueobjects.UnLocode;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 経路候補算出ドメインサービス（IT4 本実装）。
 *
 * <p>再帰的 DFS で全候補を列挙する。
 * グラフ表現は隣接リスト {@code Map<UnLocode, List<TransitEdge>>} で O(|V| + |E|) 探索。</p>
 *
 * <p>ADR-0010: IT3 PoC {@code OptimalRouteService} をゼロから再設計した正式実装。</p>
 * <p>ADR-0011: {@link com.example.cargotracker.routingms.domain.ports.EdgeRepository} 経由でデータを受け取る。</p>
 */
public class RouteCandidateFinder {

    private final Map<UnLocode, List<TransitEdge>> adjacency;

    public RouteCandidateFinder(List<TransitEdge> edges) {
        this.adjacency = buildAdjacency(edges);
    }

    private Map<UnLocode, List<TransitEdge>> buildAdjacency(List<TransitEdge> edges) {
        Map<UnLocode, List<TransitEdge>> graph = new HashMap<>();
        for (TransitEdge edge : edges) {
            graph.computeIfAbsent(edge.fromUnLocode(), k -> new ArrayList<>()).add(edge);
        }
        return graph;
    }

    public List<TransitPath> findCandidates(RouteSearchSpecification spec) {
        List<TransitPath> results = new ArrayList<>();
        dfs(spec, spec.origin(), new ArrayList<>(), results);
        return results;
    }

    private void dfs(RouteSearchSpecification spec, UnLocode current,
                     List<TransitEdge> currentPath, List<TransitPath> results) {
        if (currentPath.size() >= spec.maxLegs()) {
            return;
        }
        List<TransitEdge> neighbors = adjacency.getOrDefault(current, List.of());
        for (TransitEdge edge : neighbors) {
            if (isEligible(edge, spec, currentPath)) {
                explore(edge, spec, currentPath, results);
            }
        }
    }

    private boolean isEligible(TransitEdge edge, RouteSearchSpecification spec,
                                List<TransitEdge> currentPath) {
        return edge.acceptedCargoTypes().contains(spec.cargoType())
                && hasValidTransfer(currentPath, edge, spec);
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

    private boolean isVisited(UnLocode node, List<TransitEdge> path) {
        return path.stream()
                .anyMatch(e -> e.fromUnLocode().equals(node) || e.toUnLocode().equals(node));
    }

    private boolean hasValidTransfer(List<TransitEdge> path, TransitEdge next,
                                     RouteSearchSpecification spec) {
        if (path.isEmpty()) {
            return true;
        }
        TransitEdge last = path.get(path.size() - 1);
        return last.arrivalTime().plus(spec.minimumTransferTime())
                .isBefore(next.departureTime());
    }
}
