package com.example.cargotracker.routing.infrastructure.config;

import com.example.cargotracker.routing.application.internal.outboundservices.BookingQueryPort;
import com.example.cargotracker.routing.application.internal.outboundservices.RouteProviderPort;
import com.example.cargotracker.routing.application.internal.queryservices.RouteSearchService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

/**
 * routing コンテキストの Spring Bean 設定クラス。
 *
 * <p>{@code product} プロファイル以外でのみ有効。
 * product プロファイルでは実際の {@link RouteProviderPort} 実装が別途提供される想定のため、
 * そちらの設定でサービス Bean を登録すること。
 */
@Configuration
@Profile("!product")
public class RoutingConfig {

    @Bean
    public RouteSearchService routeSearchService(
            BookingQueryPort bookingQueryPort,
            RouteProviderPort routeProviderPort) {
        return new RouteSearchService(bookingQueryPort, routeProviderPort);
    }
}
