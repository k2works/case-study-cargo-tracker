package com.example.routingms.config;

import com.example.routingms.domain.services.OptimalRouteService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Routing コンテキストのドメインサービス Bean 定義。
 *
 * <p>{@link OptimalRouteService} はフレームワーク非依存の純粋なドメインサービスのため、
 * 設定クラスで Bean として登録しアプリケーションサービスへ注入する。</p>
 */
@Configuration
public class RoutingDomainConfig {

    @Bean
    public OptimalRouteService optimalRouteService() {
        return new OptimalRouteService();
    }
}
