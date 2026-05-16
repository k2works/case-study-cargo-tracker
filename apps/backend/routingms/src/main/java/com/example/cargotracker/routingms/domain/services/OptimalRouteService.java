package com.example.cargotracker.routingms.domain.services;

import com.example.cargotracker.routingms.domain.model.valueobjects.RouteSearchSpecification;
import com.example.cargotracker.routingms.domain.model.valueobjects.TransitEdge;
import com.example.cargotracker.routingms.domain.model.valueobjects.TransitPath;

import java.util.List;

/**
 * @deprecated IT4 で {@link RouteCandidateFinder} に置換。ADR-0010 参照。
 */
@Deprecated(since = "IT4", forRemoval = true)
public class OptimalRouteService {

    private final RouteCandidateFinder delegate;

    public OptimalRouteService(List<TransitEdge> edges) {
        this.delegate = new RouteCandidateFinder(edges);
    }

    public List<TransitPath> findCandidates(RouteSearchSpecification spec) {
        return delegate.findCandidates(spec);
    }
}
