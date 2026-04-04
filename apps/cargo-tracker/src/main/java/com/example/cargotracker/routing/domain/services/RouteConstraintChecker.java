package com.example.cargotracker.routing.domain.services;

import com.example.cargotracker.routing.domain.model.RouteSearchQuery;
import com.example.cargotracker.routing.domain.model.Voyage;

/**
 * ルート候補が検索条件を満たすかを判定するドメインサービスの共通インターフェース。
 */
public interface RouteConstraintChecker {

    /**
     * 航海が検索条件の制約を満たすかを判定する。
     *
     * @param voyage 判定対象の航海
     * @param query  検索条件
     * @return 制約を満たす場合 {@code true}
     */
    boolean satisfies(Voyage voyage, RouteSearchQuery query);
}
