package com.example.trackingms.config;

import org.axonframework.config.EventProcessingConfigurer;
import org.axonframework.extensions.kafka.eventhandling.consumer.streamable.StreamableKafkaMessageSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;

/**
 * trackingms の cross-service イベント購読 Axon 設定（ADR-0009、IT5 タスク 1.4）。
 *
 * <p>{@code tracking-issuance-requests} プロセッシンググループ
 * （{@code TrackingIssuanceRequestedEventHandler}）を Kafka（cargo-events トピック）の
 * {@link StreamableKafkaMessageSource} に束ね、tracking モードで購読する。bookingms の
 * {@code BookingSagaManager} が KafkaPublisher で発行する {@code TrackingIssuanceRequestedEvent}
 * を受信し、{@code InitializeTrackingCommand} で {@code TrackingActivity} 集約を NOT_RECEIVED
 * 初期化・採番する（IT5 1.3）。trackingms 内のイベント（{@code TrackingInitializedEvent} 等）は
 * 引き続き event store を source とする default プロセッサが処理する。</p>
 *
 * <p>受信ハンドラは ADR-0011（ホワイトリスト方式）に従い、{@code AggregateNotFoundException} /
 * {@code CommandExecutionException} の 2 種のみ WARN スキップする。</p>
 *
 * <p>Kafka を無効化したプロファイル（heroku で {@code axon.kafka.fetcher.enabled=false}）では
 * {@link StreamableKafkaMessageSource} が生成されないため、本設定を {@code @ConditionalOnProperty} で
 * スキップする。</p>
 */
@Configuration
@ConditionalOnProperty(name = "axon.kafka.fetcher.enabled", havingValue = "true")
public class KafkaEventProcessingConfig {

    @Autowired
    public void configureTrackingIssuanceRequestProcessor(EventProcessingConfigurer config) {
        config.registerTrackingEventProcessor(
                "cross-tracking-issuance-requests",
                configuration -> configuration.getComponent(StreamableKafkaMessageSource.class)
        );
    }

    /**
     * {@code HandlingActivityRegisteredEventHandler} の cross-service 購読（IT5 3.3）。
     * handlingms が発行する shared モジュールの {@code HandlingActivityRegisteredEvent} を Kafka 経由で受信し、
     * 荷役種別に応じた {@code UpdateTransportStatusCommand} を {@code TrackingActivity} 集約に送信する。
     */
    @Autowired
    public void configureHandlingActivityEventsProcessor(EventProcessingConfigurer config) {
        config.registerTrackingEventProcessor(
                "cross-handling-activity-events",
                configuration -> configuration.getComponent(StreamableKafkaMessageSource.class)
        );
    }
}
