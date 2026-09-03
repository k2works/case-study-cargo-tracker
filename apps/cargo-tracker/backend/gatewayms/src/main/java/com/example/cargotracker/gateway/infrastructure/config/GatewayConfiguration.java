package com.example.cargotracker.gateway.infrastructure.config;

import com.example.cargotracker.shared.infrastructure.security.JwtSecret;
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
            @Value("${cargo-tracker.jwt.secret:}") String secret,
            @Value("${cargo-tracker.production-like:false}") boolean productionLike) {
        // 既定値を持たせない。gateway と authms が同じ既定値に落ちると署名検証は通り、
        // クラスタは正常に見えたまま既知の鍵で運用される。
        var registration = new FilterRegistrationBean<>(
                new JwtAuthenticationFilter(JwtSecret.of(secret, productionLike).value()));
        registration.addUrlPatterns("/*");
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE);
        return registration;
    }
}
