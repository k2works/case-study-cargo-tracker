package com.example.bookingms.config;

import org.axonframework.config.EventProcessingConfigurer;
import org.axonframework.extensions.kafka.eventhandling.consumer.streamable.StreamableKafkaMessageSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;

/**
 * cross-service イベント購読の Axon 設定（US11 / ADR-0009）。
 *
 * <p>{@code route-confirmed} プロセッシンググループ（{@code RouteConfirmedEventHandler}）を
 * Kafka（cargo-events トピック）の {@link StreamableKafkaMessageSource} に束ね、tracking モードで購読する。
 * routingms が発行する {@code RouteConfirmedEvent} を受信し、{@code AssignRouteToCargoCommand} を発行する。
 * bookingms 内のイベント（CargoBookedEvent 等）は引き続き event store を source とする default プロセッサ
 * （subscribing）が処理する。</p>
 *
 * <p>Kafka を無効化したプロファイル（local-h2 smoke 等で {@code axon.kafka.fetcher.enabled=false}）では
 * {@link StreamableKafkaMessageSource} が生成されないため、本設定を {@code @ConditionalOnProperty} で
 * スキップする。その場合 {@code route-confirmed} は default プロセッサ（event store source）として起動するが、
 * cross-service イベントは Kafka からのみ届くため副作用はない。</p>
 */
@Configuration
@ConditionalOnProperty(name = "axon.kafka.fetcher.enabled", havingValue = "true")
public class KafkaEventProcessingConfig {

    @Autowired
    public void configureRouteConfirmedProcessor(EventProcessingConfigurer config) {
        config.registerTrackingEventProcessor(
                "route-confirmed",
                configuration -> configuration.getComponent(StreamableKafkaMessageSource.class)
        );
    }
}
