package com.example.bookingms.infrastructure.messaging;

import com.example.bookingms.domain.ports.CargoEventPublisher;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.JacksonJsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * メッセージング Bean 設定
 *
 * <p>RabbitMQ が利用可能な場合（{@link RabbitTemplate} が存在する場合）は
 * {@link RabbitMqCargoEventPublisher} を、そうでない場合は NoOp 実装を登録する。
 * メッセージは JSON 形式でシリアライズする。
 */
@Configuration
public class MessagingConfiguration {

    @Bean
    @ConditionalOnBean(RabbitTemplate.class)
    public MessageConverter jacksonMessageConverter() {
        return new JacksonJsonMessageConverter();
    }

    @Bean
    @ConditionalOnBean(RabbitTemplate.class)
    public CargoEventPublisher rabbitMqCargoEventPublisher(RabbitTemplate rabbitTemplate,
                                                           MessageConverter messageConverter) {
        rabbitTemplate.setMessageConverter(messageConverter);
        return new RabbitMqCargoEventPublisher(rabbitTemplate);
    }

    @Bean
    @ConditionalOnMissingBean(CargoEventPublisher.class)
    public CargoEventPublisher noOpCargoEventPublisher() {
        return event -> { /* RabbitMQ 未設定: イベントを破棄 */ };
    }
}
