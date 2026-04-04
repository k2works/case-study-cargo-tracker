package com.example.cargotracker.routing.domain.services;

import com.example.cargotracker.routing.domain.model.RouteSearchQuery;
import com.example.cargotracker.routing.domain.model.Voyage;

/**
 * 貨物種別制約チェッカー。
 *
 * <p>航海が要求された貨物種別に対応しているかを検証する。
 */
public class CargoTypeConstraint implements RouteConstraintChecker {

    @Override
    public boolean satisfies(Voyage voyage, RouteSearchQuery query) {
        return voyage.supports(query.cargoType());
    }
}
