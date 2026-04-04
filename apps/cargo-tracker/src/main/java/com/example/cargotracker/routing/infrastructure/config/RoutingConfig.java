package com.example.cargotracker.routing.infrastructure.config;

import com.example.cargotracker.routing.application.internal.outboundservices.BookingQueryPort;
import com.example.cargotracker.routing.application.internal.outboundservices.RouteProviderPort;
import com.example.cargotracker.routing.application.internal.outboundservices.VoyageQueryPort;
import com.example.cargotracker.routing.application.internal.queryservices.RouteDesignConditionQueryService;
import com.example.cargotracker.routing.application.internal.queryservices.RouteSearchService;
import com.example.cargotracker.routing.application.internal.queryservices.VoyageScheduleSearchService;
import com.example.cargotracker.routing.domain.services.CargoTypeConstraint;
import com.example.cargotracker.routing.domain.services.CompositeRouteConstraintChecker;
import com.example.cargotracker.routing.domain.services.DeadlineConstraint;
import com.example.cargotracker.routing.domain.services.RouteConstraintChecker;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

/**
 * routing コンテキストの Spring Bean 設定クラス。
 *
 * <p>{@link RouteDesignConditionQueryService} は常に登録される。
 * {@link RouteSearchService} は {@link RouteProviderPort} 実装が存在する場合のみ登録される。
 * {@code product} プロファイル以外では {@link StubRouteProviderAdapter} が提供する。
 */
@Configuration
public class RoutingConfig {

    @Bean
    public RouteDesignConditionQueryService routeDesignConditionQueryService(
            BookingQueryPort bookingQueryPort) {
        return new RouteDesignConditionQueryService(bookingQueryPort);
    }

    @Bean
    public VoyageScheduleSearchService voyageScheduleSearchService(VoyageQueryPort voyageQueryPort) {
        return new VoyageScheduleSearchService(voyageQueryPort);
    }

    @Bean
    @Profile("product")
    public RouteConstraintChecker routeConstraintChecker() {
        return new CompositeRouteConstraintChecker(
            new DeadlineConstraint(), new CargoTypeConstraint()
        );
    }

    @Bean
    @ConditionalOnBean(RouteProviderPort.class)
    public RouteSearchService routeSearchService(
            BookingQueryPort bookingQueryPort,
            RouteProviderPort routeProviderPort) {
        return new RouteSearchService(bookingQueryPort, routeProviderPort);
    }
}
