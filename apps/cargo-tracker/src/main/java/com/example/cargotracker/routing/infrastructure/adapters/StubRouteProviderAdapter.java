package com.example.cargotracker.routing.infrastructure.adapters;

import com.example.cargotracker.routing.application.internal.outboundservices.RouteProviderPort;
import com.example.cargotracker.routing.domain.model.RouteCandidate;
import com.example.cargotracker.routing.domain.model.RouteSearchQuery;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.List;

/**
 * 開発・テスト用のスタブ {@link RouteProviderPort} 実装。
 *
 * <p>外部ルートプロバイダーが未実装の間、固定のルート候補 2 件を返す。
 * {@code product} プロファイル以外でのみ有効。
 */
@Component
@Profile("!product")
public class StubRouteProviderAdapter implements RouteProviderPort {

    @Override
    public List<RouteCandidate> findRoutes(RouteSearchQuery query) {
        return List.of(
            new RouteCandidate(
                "SG001",
                List.of("SGSIN", "JPTYO"),
                14,
                new BigDecimal("150000"),
                query.requestedArrivalDate().minusDays(2)
            ),
            new RouteCandidate(
                "SG002",
                List.of("SGSIN", "KRPUS", "JPTYO"),
                18,
                new BigDecimal("120000"),
                query.requestedArrivalDate().minusDays(1)
            )
        );
    }
}
