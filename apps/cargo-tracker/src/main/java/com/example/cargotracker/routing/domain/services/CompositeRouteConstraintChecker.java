package com.example.cargotracker.routing.domain.services;

import com.example.cargotracker.routing.domain.model.RouteSearchQuery;
import com.example.cargotracker.routing.domain.model.Voyage;

import java.util.Arrays;
import java.util.List;

/**
 * 複数の {@link RouteConstraintChecker} を AND 結合する複合チェッカー。
 *
 * <p>チェッカーが 0 件の場合、{@link #satisfies} は常に {@code true} を返す（AND 結合の恒等元）。
 */
public class CompositeRouteConstraintChecker implements RouteConstraintChecker {

    private final List<RouteConstraintChecker> checkers;

    public CompositeRouteConstraintChecker(RouteConstraintChecker... checkers) {
        this.checkers = List.copyOf(Arrays.asList(checkers));
    }

    @Override
    public boolean satisfies(Voyage voyage, RouteSearchQuery query) {
        return checkers.stream().allMatch(c -> c.satisfies(voyage, query));
    }
}
