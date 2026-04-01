package com.example.cargotracker.quote.infrastructure.adapters;

import com.example.cargotracker.quote.application.internal.outboundservices.QuoteRouteProviderPort;
import com.example.cargotracker.quote.domain.model.valueobjects.QuoteCondition;
import com.example.cargotracker.quote.domain.model.valueobjects.RouteOption;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.List;

/**
 * 固定ルート候補を返す stub アダプター。
 * product プロファイル以外の環境（開発・テスト）で有効になる。
 */
@Component
@Profile("!product")
public class StubQuoteRouteProviderAdapter implements QuoteRouteProviderPort {

    @Override
    public List<RouteOption> findRouteOptions(QuoteCondition condition) {
        return List.of(
                new RouteOption(
                        List.of("SGSIN", "JPTYO"),
                        14,
                        new BigDecimal("150000"),
                        "SG001"
                ),
                new RouteOption(
                        List.of("SGSIN", "KRPUS", "JPTYO"),
                        18,
                        new BigDecimal("120000"),
                        "SG002"
                ),
                new RouteOption(
                        List.of("JPTYO"),
                        7,
                        new BigDecimal("200000"),
                        "JP001"
                )
        );
    }
}
