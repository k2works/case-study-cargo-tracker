package com.example.trackingms.infrastructure.messaging;

import com.example.trackingms.domain.ports.TrackingEventPublisher;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.JacksonJsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * trackingms メッセージング Bean 設定
 *
 * <p>{@link RabbitTemplate} がクラスパス上に存在する場合（spring-rabbit が依存に含まれる場合）は
 * {@link RabbitMqTrackingEventPublisher} を、そうでない場合は NoOp 実装を登録する。
 *
 * <p>ユーザー定義の {@code @Configuration} クラスでは {@code @ConditionalOnBean} は
 * AutoConfiguration の処理順に依存して評価が不安定なため、{@code @ConditionalOnClass} を使う。
 */
@Configuration
@ConditionalOnClass(RabbitTemplate.class)
public class TrackingMessagingConfiguration {

    @Bean
    public MessageConverter jacksonMessageConverter() {
        return new JacksonJsonMessageConverter();
    }

    @Bean
    public TrackingEventPublisher rabbitMqTrackingEventPublisher(RabbitTemplate rabbitTemplate,
                                                                  MessageConverter messageConverter) {
        rabbitTemplate.setMessageConverter(messageConverter);
        return new RabbitMqTrackingEventPublisher(rabbitTemplate);
    }

    @Bean
    @ConditionalOnMissingBean(TrackingEventPublisher.class)
    public TrackingEventPublisher noOpTrackingEventPublisher() {
        return new NoOpTrackingEventPublisher();
    }

    // --- bookingms へ発行するイベント用エクスチェンジ ---

    @Bean
    public TopicExchange trackingEventsExchange() {
        return new TopicExchange("tracking.events", true, false);
    }
}
