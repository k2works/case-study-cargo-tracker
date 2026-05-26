package com.example.routingms.domain.model;

import java.time.LocalDate;

/**
 * 経路探索の制約条件（US08 / Routing Context）。
 *
 * <p>出発地・目的地・到着期限・貨物種別を指定して、{@code OptimalRouteService} が
 * 経路候補を算出する際の入力となる値オブジェクト。生成時に不変条件を検証する。</p>
 */
public record RouteSearchSpecification(
        String origin,
        String destination,
        LocalDate arrivalDeadline,
        String cargoType
) {
    public RouteSearchSpecification {
        if (origin == null || origin.isBlank()) {
            throw new IllegalArgumentException("出発地は必須です");
        }
        if (destination == null || destination.isBlank()) {
            throw new IllegalArgumentException("目的地は必須です");
        }
        if (origin.equals(destination)) {
            throw new IllegalArgumentException("出発地と目的地は異なる必要があります");
        }
        if (arrivalDeadline == null) {
            throw new IllegalArgumentException("到着期限は必須です");
        }
        if (cargoType == null || cargoType.isBlank()) {
            throw new IllegalArgumentException("貨物種別は必須です");
        }
    }
}
