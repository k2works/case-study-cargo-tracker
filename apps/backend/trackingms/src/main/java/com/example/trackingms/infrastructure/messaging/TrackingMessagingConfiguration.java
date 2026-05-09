package com.example.trackingms.infrastructure.messaging;

import com.example.trackingms.domain.ports.TrackingEventPublisher;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.JacksonJsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * trackingms メッセージング Bean 設定
 *
 * <p>RabbitMQ が利用可能な場合は {@link RabbitMqTrackingEventPublisher} を、
 * そうでない場合は NoOp 実装を登録する。
 */
@Configuration
public class TrackingMessagingConfiguration {

    @Bean
    @ConditionalOnBean(RabbitTemplate.class)
    public MessageConverter jacksonMessageConverter() {
        return new JacksonJsonMessageConverter();
    }

    @Bean
    @ConditionalOnBean(RabbitTemplate.class)
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
}
