package com.example.cargotracker.quote.infrastructure.adapters;

import com.example.cargotracker.quote.application.internal.outboundservices.QuoteRouteProviderPort;
import com.example.cargotracker.quote.domain.model.valueobjects.QuoteCondition;
import com.example.cargotracker.quote.domain.model.valueobjects.RouteOption;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;

/**
 * HTTP 経由でルート候補を取得するアダプター。
 * product プロファイルで有効になる。
 */
@Component
@Profile("product")
public class RouteProviderRestAdapter implements QuoteRouteProviderPort {

    private static final Logger log =
            LoggerFactory.getLogger(RouteProviderRestAdapter.class);

    private final RestTemplate restTemplate;
    private final String routeProviderUrl;

    public RouteProviderRestAdapter(
            RestTemplate routeProviderRestTemplate,
            @Value("${app.route-provider.url}") String routeProviderUrl) {
        this.restTemplate = routeProviderRestTemplate;
        this.routeProviderUrl = routeProviderUrl;
    }

    @Override
    public List<RouteOption> findRouteOptions(QuoteCondition condition) {
        String url = UriComponentsBuilder
                .fromUriString(routeProviderUrl + "/route-options")
                .queryParam("origin", condition.originLocode())
                .queryParam("destination", condition.destinationLocode())
                .toUriString();

        try {
            ResponseEntity<List<RouteOptionDto>> response = restTemplate.exchange(
                    url,
                    HttpMethod.GET,
                    null,
                    new ParameterizedTypeReference<>() {
                    }
            );
            List<RouteOptionDto> responseBody = response.getBody();
            if (responseBody == null) {
                return Collections.emptyList();
            }

            return responseBody.stream()
                    .map(dto -> new RouteOption(
                            dto.viaLocodes(),
                            dto.transitDays(),
                            dto.estimatedPrice(),
                            dto.voyageNumber()
                    ))
                    .toList();

        } catch (Exception e) {
            log.warn("ルートプロバイダーへの接続に失敗しました: {}", e.getMessage());
            return Collections.emptyList();
        }
    }

    record RouteOptionDto(
            List<String> viaLocodes,
            int transitDays,
            BigDecimal estimatedPrice,
            String voyageNumber
    ) {
    }

    /**
     * RouteProviderRestAdapter が利用する RestTemplate Bean。
     * product プロファイルでのみ生成される。
     */
    @Configuration
    @Profile("product")
    static class RestTemplateConfig {
        @Bean
        public RestTemplate routeProviderRestTemplate() {
            return new RestTemplate();
        }
    }
}
