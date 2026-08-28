package com.example.routingms.config;

import com.example.shared.auth.AuthenticatedUserFilter;
import java.time.Clock;
import java.time.ZoneId;
import org.springframework.beans.factory.annotation.Value;
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
    public ZoneId businessZone(
            @Value("${app.business-time-zone:Asia/Tokyo}") String businessZone) {
        return ZoneId.of(businessZone);
    }

    @Bean
    public Clock clock(ZoneId businessZone) {
        // 「いま」は業務タイムゾーンの時計で決める。UTC で判断すると、時差の分だけ
        // 「もう出た / まだ出ていない」の境目がずれる。
        return Clock.system(businessZone);
    }
}
