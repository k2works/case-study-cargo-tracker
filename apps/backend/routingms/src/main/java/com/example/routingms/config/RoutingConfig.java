package com.example.routingms.config;

import com.example.routingms.application.internal.RegisterVoyageUseCase;
import com.example.routingms.application.internal.SearchVoyageUseCase;
import com.example.routingms.application.port.VoyageRepository;
import com.example.shared.auth.AuthenticatedUserFilter;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;

@Configuration
public class RoutingConfig {

    /**
     * Gateway が付けた利用者ヘッダを必須とする（ADR-007）。
     *
     * <p>認可を書き忘れたエンドポイントが無認証で開くことを、認可判定より前で塞ぐ。
     * 公開エンドポイントを持たないため、除外はヘルスチェックだけである。
     */
    @Bean
    public FilterRegistrationBean<AuthenticatedUserFilter> authenticatedUserFilter() {
        FilterRegistrationBean<AuthenticatedUserFilter> registration =
                new FilterRegistrationBean<>(new AuthenticatedUserFilter());
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE);
        return registration;
    }

    @Bean
    public RegisterVoyageUseCase registerVoyageUseCase(VoyageRepository voyages) {
        return new RegisterVoyageUseCase(voyages);
    }

    @Bean
    public SearchVoyageUseCase searchVoyageUseCase(VoyageRepository voyages) {
        return new SearchVoyageUseCase(voyages);
    }
}
