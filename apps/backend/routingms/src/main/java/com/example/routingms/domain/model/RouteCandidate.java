package com.example.routingms.domain.model;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 経路候補（US08 / Routing Context）。
 *
 * <p>{@code OptimalRouteService} が算出する経路の候補。1 つ以上の輸送区間（{@link RouteLeg}）と
 * 所要日数・概算費用を保持する。永続化される集約ではなく、算出結果として返す値オブジェクト。</p>
 */
public record RouteCandidate(
        List<RouteLeg> legs,
        int estimatedDays,
        BigDecimal estimatedCost,
        String currency
) {
    public RouteCandidate {
        if (legs == null || legs.isEmpty()) {
            throw new IllegalArgumentException("経路候補は 1 区間以上必要です");
        }
        legs = List.copyOf(legs);
    }

    /** 直行便（単一区間）かどうか。 */
    public boolean isDirect() {
        return legs.size() == 1;
    }

    /** 経由港を含む航海番号の列（積込港の順序）。 */
    public List<String> voyageNumbers() {
        return legs.stream().map(RouteLeg::voyageNumber).toList();
    }

    /** 出発時刻（最初の区間の積込時刻）。 */
    public LocalDateTime departureTime() {
        return legs.get(0).loadTime();
    }

    /** 到着時刻（最終区間の荷降し時刻）。 */
    public LocalDateTime arrivalTime() {
        return legs.get(legs.size() - 1).unloadTime();
    }
}
