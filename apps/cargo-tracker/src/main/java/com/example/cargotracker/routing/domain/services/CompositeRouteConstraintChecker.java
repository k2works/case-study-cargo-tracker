package com.example.cargotracker.routing.domain.services;

import com.example.cargotracker.routing.domain.model.RouteSearchQuery;
import com.example.cargotracker.routing.domain.model.Voyage;

import java.util.Arrays;
import java.util.List;

/**
 * 複数の {@link RouteConstraintChecker} を AND 結合する複合チェッカー。
 */
public class CompositeRouteConstraintChecker implements RouteConstraintChecker {

    private final List<RouteConstraintChecker> checkers;

    public CompositeRouteConstraintChecker(RouteConstraintChecker... checkers) {
        this.checkers = Arrays.asList(checkers);
    }

    @Override
    public boolean satisfies(Voyage voyage, RouteSearchQuery query) {
        return checkers.stream().allMatch(c -> c.satisfies(voyage, query));
    }
}
