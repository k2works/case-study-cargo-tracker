package com.example.cargotracker.shared.infrastructure.axon;

import org.axonframework.messaging.queryhandling.gateway.QueryGateway;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * {@link QueryDispatcher} を組み立てる。
 *
 * <p>共有設定は一括スキャンにせず、必要なサービスだけが {@code @Import} で取り込む。
 * 一括にすると、問い合わせを持たない gatewayms まで {@code QueryGateway} を求めて
 * 起動に失敗する。</p>
 */
@Configuration
public class QueryDispatcherConfiguration {

    @Bean
    public QueryDispatcher queryDispatcher(QueryGateway queryGateway) {
        return new QueryDispatcher(queryGateway);
    }
}
