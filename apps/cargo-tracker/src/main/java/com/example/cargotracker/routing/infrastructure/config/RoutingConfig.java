package com.example.cargotracker.routing.infrastructure.config;

import com.example.cargotracker.routing.application.internal.outboundservices.BookingQueryPort;
import com.example.cargotracker.routing.application.internal.outboundservices.RouteProviderPort;
import com.example.cargotracker.routing.application.internal.queryservices.RouteSearchService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * routing コンテキストの Spring Bean 設定クラス。
 *
 * <p>{@link RouteProviderPort} の実装が Bean として登録されている場合にのみ有効になる。
 * {@code product} プロファイルでは {@link RouteProviderPort} 実装を別途提供することで
 * 自動的に有効化される。
 */
@Configuration
@ConditionalOnBean(RouteProviderPort.class)
public class RoutingConfig {

    @Bean
    public RouteSearchService routeSearchService(
            BookingQueryPort bookingQueryPort,
            RouteProviderPort routeProviderPort) {
        return new RouteSearchService(bookingQueryPort, routeProviderPort);
    }
}
