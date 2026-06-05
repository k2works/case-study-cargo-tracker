package com.example.handlingms.config;

import org.axonframework.config.EventProcessingConfigurer;
import org.axonframework.extensions.kafka.eventhandling.consumer.streamable.StreamableKafkaMessageSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;

/**
 * cross-service イベント購読の Axon 設定（IT5 3.1 / ADR-0009）。
 *
 * <p>{@code cargo-snapshot} プロセッシンググループ（{@code CargoSnapshotProjectionEventHandler}）を
 * Kafka（cargo-events トピック）の {@link StreamableKafkaMessageSource} に束ね、tracking モードで購読する。
 * bookingms / trackingms が発行する {@code TrackingIssuanceRequestedEvent} / {@code CargoTrackedEvent} を
 * 受信し、handlingms 専用の cargo_snapshot Read Model に投影する。</p>
 *
 * <p>Kafka を無効化したプロファイル（local-h2 smoke 等で {@code axon.kafka.fetcher.enabled=false}）では
 * {@link StreamableKafkaMessageSource} が生成されないため、本設定を {@code @ConditionalOnProperty} で
 * スキップする。</p>
 */
@Configuration
@ConditionalOnProperty(name = "axon.kafka.fetcher.enabled", havingValue = "true")
public class KafkaEventProcessingConfig {

    @Autowired
    public void configureCargoSnapshotProcessor(EventProcessingConfigurer config) {
        config.registerTrackingEventProcessor(
                "cross-cargo-snapshot",
                configuration -> configuration.getComponent(StreamableKafkaMessageSource.class)
        );
    }
}
