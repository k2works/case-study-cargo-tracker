package com.example.cargotracker.shared.infrastructure.axon;

import java.time.Duration;
import org.axonframework.axonserver.connector.AxonServerConnectionManager;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 起動時接続検査の配線。全サービスが取り込む。
 *
 * <p>既定で有効。Axon Server を立てないテストだけが
 * {@code cargo-tracker.axon.startup-check.enabled=false} で外せる。既定を無効にすると
 * 「本番でだけ効いていない」状態に気づけないため、既定は有効のままにする。</p>
 */
@Configuration
@ConditionalOnProperty(
        prefix = "cargo-tracker.axon.startup-check",
        name = "enabled",
        havingValue = "true",
        matchIfMissing = true)
public class AxonServerStartupCheckConfiguration {

    @Bean
    public AxonServerStartupCheck axonServerStartupCheck(AxonServerConnectionManager connectionManager) {
        return new AxonServerStartupCheck(connectionManager, Duration.ofSeconds(30));
    }
}
