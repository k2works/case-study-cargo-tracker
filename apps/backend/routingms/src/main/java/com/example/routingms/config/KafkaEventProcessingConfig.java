package com.example.routingms.config;

import org.axonframework.config.EventProcessingConfigurer;
import org.axonframework.extensions.kafka.eventhandling.consumer.streamable.StreamableKafkaMessageSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;

/**
 * cross-service イベント購読の Axon 設定（ADR-0009、T7）。
 *
 * <p>{@code route-design-requests} プロセッシンググループ（{@code RouteDesignRequestEventHandler}）を
 * Kafka（cargo-events トピック）の {@link StreamableKafkaMessageSource} に束ね、tracking モードで購読する。
 * bookingms が KafkaPublisher で発行する {@code RouteDesignRequestedEvent} を受信する。
 * routingms 内のイベント（VoyageRegisteredEvent 等）は引き続き event store を source とする
 * default プロセッサが処理する。</p>
 *
 * <p>Kafka を無効化したプロファイル（local-h2 smoke 等で {@code axon.kafka.fetcher.enabled=false}）では
 * {@link StreamableKafkaMessageSource} が生成されないため、本設定を {@code @ConditionalOnProperty} で
 * スキップする。その場合 {@code route-design-requests} は default プロセッサ（event store source）として
 * 起動するが、cross-service イベントは Kafka からのみ届くため副作用はない。</p>
 */
@Configuration
@ConditionalOnProperty(name = "axon.kafka.fetcher.enabled", havingValue = "true")
public class KafkaEventProcessingConfig {

    @Autowired
    public void configureRouteDesignRequestProcessor(EventProcessingConfigurer config) {
        config.registerTrackingEventProcessor(
                "route-design-requests",
                configuration -> configuration.getComponent(StreamableKafkaMessageSource.class)
        );
    }
}
