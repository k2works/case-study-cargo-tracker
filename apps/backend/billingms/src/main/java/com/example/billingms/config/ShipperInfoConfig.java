package com.example.billingms.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.time.Duration;

/**
 * cross-service 荷主情報 ACL の Spring 設定（IT8 T4.1 / ADR-0015 後半）。
 *
 * <p>{@code shipper-info.adapter=rest} 時に bookingms 向け {@link RestClient} を生成する。
 * Caffeine cache（TTL 5min）は application.yml の {@code spring.cache.caffeine.spec} で設定する
 * （{@code @EnableCaching} を有効化）。</p>
 */
@Configuration
@EnableConfigurationProperties(ShipperInfoProperties.class)
@EnableCaching
public class ShipperInfoConfig {

    /**
     * bookingms 向け {@link RestClient} Bean（adapter=rest 時のみ）。
     * Bean 名 {@code bookingmsRestClient} は {@code RestShipperInfoAcl} が直接参照する。
     */
    @Bean(name = "bookingmsRestClient")
    @ConditionalOnProperty(name = "shipper-info.adapter", havingValue = "rest")
    public RestClient bookingmsRestClient(ShipperInfoProperties properties) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofMillis(properties.bookingms().timeoutMs()));
        factory.setReadTimeout(Duration.ofMillis(properties.bookingms().timeoutMs()));
        return RestClient.builder()
                .baseUrl(properties.bookingms().baseUrl())
                .requestFactory(factory)
                .build();
    }
}
