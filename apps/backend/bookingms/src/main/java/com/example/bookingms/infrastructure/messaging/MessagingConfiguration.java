package com.example.bookingms.infrastructure.messaging;

import com.example.bookingms.domain.ports.CargoEventPublisher;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.JacksonJsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * メッセージング Bean 設定
 *
 * <p>{@link RabbitTemplate} がクラスパス上に存在する場合（spring-rabbit が依存に含まれる場合）は
 * {@link RabbitMqCargoEventPublisher} を、そうでない場合は NoOp 実装を登録する。
 * メッセージは JSON 形式でシリアライズする。
 *
 * <p>ユーザー定義の {@code @Configuration} クラスでは {@code @ConditionalOnBean} は
 * AutoConfiguration の処理順に依存して評価が不安定なため、{@code @ConditionalOnClass} を使う。
 */
@Configuration
@ConditionalOnClass(RabbitTemplate.class)
public class MessagingConfiguration {

    @Bean
    public MessageConverter jacksonMessageConverter() {
        return new JacksonJsonMessageConverter();
    }

    @Bean
    public CargoEventPublisher rabbitMqCargoEventPublisher(RabbitTemplate rabbitTemplate,
                                                           MessageConverter messageConverter) {
        rabbitTemplate.setMessageConverter(messageConverter);
        return new RabbitMqCargoEventPublisher(rabbitTemplate);
    }

    // --- bookingms から発行するイベント用エクスチェンジ ---

    @Bean
    public TopicExchange cargoEventsExchange() {
        return new TopicExchange(RabbitMqCargoEventPublisher.EXCHANGE, true, false);
    }

    @Bean
    @ConditionalOnMissingBean(CargoEventPublisher.class)
    public CargoEventPublisher noOpCargoEventPublisher() {
        return new NoOpCargoEventPublisher();
    }

    // --- trackingms からのイベント受信用キュー・バインディング ---

    @Bean
    public TopicExchange trackingEventsExchange() {
        return new TopicExchange("tracking.events", true, false);
    }

    @Bean
    public Queue trackingNumberIssuedQueue() {
        return new Queue(TrackingNumberIssuedEventListener.QUEUE, true);
    }

    @Bean
    public Binding trackingNumberIssuedBinding(Queue trackingNumberIssuedQueue,
                                                TopicExchange trackingEventsExchange) {
        return BindingBuilder.bind(trackingNumberIssuedQueue)
                .to(trackingEventsExchange)
                .with("tracking.number.issued");
    }
}
