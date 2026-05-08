package com.example.bookingms.infrastructure.messaging;

import com.example.bookingms.domain.events.CargoRoutedEvent;
import com.example.bookingms.domain.ports.CargoEventPublisher;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

/**
 * RabbitMQ を用いた CargoEventPublisher 実装
 *
 * <p>Bean 登録は {@link MessagingConfiguration} が担当する。
 */
public class RabbitMqCargoEventPublisher implements CargoEventPublisher {

    static final String EXCHANGE = "cargo.events";
    static final String ROUTING_KEY_CARGO_ROUTED = "cargo.routed";

    private final RabbitTemplate rabbitTemplate;

    public RabbitMqCargoEventPublisher(RabbitTemplate rabbitTemplate) {
        this.rabbitTemplate = rabbitTemplate;
    }

    @Override
    public void publishCargoRouted(CargoRoutedEvent event) {
        rabbitTemplate.convertAndSend(EXCHANGE, ROUTING_KEY_CARGO_ROUTED, event);
    }
}
