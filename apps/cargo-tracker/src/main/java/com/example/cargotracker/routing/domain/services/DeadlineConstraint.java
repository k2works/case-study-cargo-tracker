package com.example.cargotracker.routing.domain.services;

import com.example.cargotracker.routing.domain.model.RouteSearchQuery;
import com.example.cargotracker.routing.domain.model.Voyage;

/**
 * 期限制約チェッカー。
 *
 * <p>航海の最終レグの到着日が希望着日以前であるかを検証する。
 */
public class DeadlineConstraint implements RouteConstraintChecker {

    @Override
    public boolean satisfies(Voyage voyage, RouteSearchQuery query) {
        return !voyage.latestArrivalDate().isAfter(query.requestedArrivalDate());
    }
}
