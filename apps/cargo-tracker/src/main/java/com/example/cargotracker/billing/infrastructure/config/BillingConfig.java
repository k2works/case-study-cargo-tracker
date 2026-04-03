package com.example.cargotracker.billing.infrastructure.config;

import com.example.cargotracker.billing.domain.model.services.FreightCalculationService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * billing コンテキストの Spring Bean 設定クラス。
 *
 * <p>ドメイン層の {@link FreightCalculationService} は Spring アノテーションを持たないため、
 * このクラスで Bean として明示的に登録する。
 */
@Configuration
public class BillingConfig {

    @Bean
    public FreightCalculationService freightCalculationService() {
        return new FreightCalculationService();
    }
}
