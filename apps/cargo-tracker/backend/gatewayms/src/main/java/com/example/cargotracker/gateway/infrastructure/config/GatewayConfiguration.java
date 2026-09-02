package com.example.cargotracker.gateway.infrastructure.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;

/**
 * Gateway の配線。ルーティングは application.yml に置く。
 *
 * <p>フィルタは最優先で通す。後ろに置くと、ルーティングが先に走って
 * 検証を通らない経路ができる。</p>
 */
@Configuration
public class GatewayConfiguration {

    @Bean
    public FilterRegistrationBean<JwtAuthenticationFilter> jwtAuthenticationFilter(
            @Value("${cargo-tracker.jwt.secret:cargo-tracker-development-secret-key-32bytes!}")
            String secret) {
        var registration = new FilterRegistrationBean<>(new JwtAuthenticationFilter(secret));
        registration.addUrlPatterns("/*");
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE);
        return registration;
    }
}
