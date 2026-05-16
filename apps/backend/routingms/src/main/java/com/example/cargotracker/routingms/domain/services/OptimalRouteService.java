package com.example.cargotracker.routingms.domain.services;

import com.example.cargotracker.routingms.domain.model.valueobjects.RouteSearchSpecification;
import com.example.cargotracker.routingms.domain.model.valueobjects.TransitEdge;
import com.example.cargotracker.routingms.domain.model.valueobjects.TransitPath;

import java.util.ArrayList;
import java.util.List;

/**
 * US08 先行スパイク: BFS による経路候補算出サービス。
 * ポートをノード、TransitEdge を有向エッジとしてグラフ探索を行う。
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
            if (!edge.fromUnLocode().equals(current)) {
                continue;
            }
            if (!acceptsCargo(edge, spec)) {
                continue;
            }
            if (!hasValidTransfer(currentPath, edge)) {
                continue;
            }

            List<TransitEdge> newPath = new ArrayList<>(currentPath);
            newPath.add(edge);

            if (edge.toUnLocode().equals(spec.destination())) {
                if (!edge.arrivalTime().toLocalDate().isAfter(spec.arrivalDeadline())) {
                    results.add(new TransitPath(newPath));
                }
            } else {
                String nextNode = edge.toUnLocode();
                boolean visited = currentPath.stream()
                        .anyMatch(e -> e.fromUnLocode().equals(nextNode) || e.toUnLocode().equals(nextNode));
                if (!visited) {
                    dfs(spec, nextNode, newPath, results);
                }
            }
        }
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
