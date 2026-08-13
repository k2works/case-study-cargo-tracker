package com.example.cargotracker.routing.infrastructure.config;

import com.example.cargotracker.routing.domain.model.FreightEstimator;
import com.example.cargotracker.routing.domain.model.RouteSearchService;
import java.math.BigDecimal;
import java.time.Clock;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Routing のドメインサービスを組み立てる。
 *
 * <p><strong>ドメインは Spring を知らない</strong>（ArchUnit ルール 2）。
 * 依存を注入する側をインフラ層に置く。
 *
 * <p>概算費用の単価と割増率は設定値である（ADR-008）。
 * <strong>ソースを変えずに調整できることが、この式が暫定であることの証拠になる。</strong>
 */
@Configuration
public class RoutingDomainConfiguration {

    @Bean
    public FreightEstimator freightEstimator(
            @Value("${app.freight.rate-per-ton-day:12000}") BigDecimal ratePerTonDay,
            @Value("${app.freight.hazardous-surcharge-rate:1.5}") BigDecimal hazardousRate) {
        return new FreightEstimator(ratePerTonDay, hazardousRate);
    }

    /**
     * 経路探索。
     *
     * <p>期限の判定に使うタイムゾーンは<strong>業務の時計から取る</strong>。
     * サーバの標準時で判断すると、時差の分だけ「期限当日」を取りこぼす。
     */
    @Bean
    public RouteSearchService routeSearchService(FreightEstimator estimator, Clock clock) {
        return new RouteSearchService(estimator, clock.getZone());
    }
}
