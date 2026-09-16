package com.example.cargotracker.billing.infrastructure.config;

import com.example.cargotracker.billing.domain.service.DiscountPolicy;
import com.example.cargotracker.billing.domain.service.FreightChargeCalculator;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * ドメインサービスを Bean にする。
 *
 * <p><b>ドメインにフレームワークの注釈を付けない</b>（ArchUnit が禁じている）。
 * 代わりに infrastructure 層で組み立てる。集約のコマンドハンドラは引数で受け取る
 * ので、Axon の構成にも同じ Bean が渡る。</p>
 */
@Configuration
public class BillingDomainServiceConfiguration {

    @Bean
    public FreightChargeCalculator freightChargeCalculator() {
        return new FreightChargeCalculator();
    }

    @Bean
    public DiscountPolicy discountPolicy() {
        return new DiscountPolicy();
    }
}
