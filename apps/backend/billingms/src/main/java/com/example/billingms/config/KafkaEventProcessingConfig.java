package com.example.billingms.config;

import org.axonframework.config.EventProcessingConfigurer;
import org.axonframework.extensions.kafka.eventhandling.consumer.streamable.StreamableKafkaMessageSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;

/**
 * billingms の cross-service イベント購読 Axon 設定（ADR-0009 / ADR-0015、IT7 タスク 1.3）。
 *
 * <p>{@code cross-billing} プロセッシンググループ（{@code CrossCargoDeliveredEventHandler}）を
 * Kafka（{@code cargo-events} トピック）の {@link StreamableKafkaMessageSource} に束ね、
 * tracking モードで購読する。trackingms の {@code TrackingActivity} 集約が集約発火する
 * {@code CargoDeliveredEvent}（shared kernel、ADR-0012）を受信し、
 * {@code CalculateInvoiceCommand} で {@code Invoice} 集約を CALCULATED 状態に初期化する
 * （IT7 US21 / タスク 2.3）。冪等化は集約内 {@code if (billingStatus != null) return;} で担保。</p>
 *
 * <p>受信ハンドラは ADR-0011（ホワイトリスト方式）に従い、{@code AggregateNotFoundException} /
 * {@code CommandExecutionException} の 2 種のみ WARN スキップする。プロセッシンググループ命名は
 * ADR-0014/0016 規約準拠（{@code cross-} prefix）。</p>
 *
 * <p>Kafka を無効化したプロファイル（heroku で {@code axon.kafka.fetcher.enabled=false}）では
 * {@link StreamableKafkaMessageSource} が生成されないため、本設定を {@code @ConditionalOnProperty} で
 * スキップする。</p>
 */
@Configuration
@ConditionalOnProperty(name = "axon.kafka.fetcher.enabled", havingValue = "true")
public class KafkaEventProcessingConfig {

    @Autowired
    public void configureCrossBillingProcessor(EventProcessingConfigurer config) {
        config.registerTrackingEventProcessor(
                "cross-billing",
                configuration -> configuration.getComponent(StreamableKafkaMessageSource.class)
        );
    }
}
