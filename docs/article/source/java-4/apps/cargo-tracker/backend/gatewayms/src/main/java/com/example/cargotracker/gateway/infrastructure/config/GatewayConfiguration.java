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
        // レート制限の 1 つ後ろ。ルーティングより前であればよい。
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE + 1);
        return registration;
    }

    /**
     * 公開照会の総当たり対策（US18 / ui_design.md）。
     *
     * <p><b>認証より先に通す。</b> 断ると決めた要求に署名の検証まで走らせると、
     * 総当たりのあいだ Gateway が一番重い処理をし続ける。公開経路はそもそも
     * 認証を通らないので、順序を変えても守りは弱くならない。</p>
     */
    @Bean
    public FilterRegistrationBean<PublicTrackingRateLimitFilter> publicTrackingRateLimitFilter(
            java.time.Clock clock) {
        var registration = new FilterRegistrationBean<>(new PublicTrackingRateLimitFilter(clock));
        registration.addUrlPatterns("/*");
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE);
        return registration;
    }
}
