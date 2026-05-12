package com.example.bookingms.infrastructure.messaging;

import com.example.bookingms.domain.events.CargoAssignedForRoutingEvent;
import com.example.bookingms.domain.events.CargoRoutedEvent;
import com.example.bookingms.domain.ports.CargoEventPublisher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.AmqpConnectException;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

/**
 * RabbitMQ を用いた CargoEventPublisher 実装
 *
 * <p>Bean 登録は {@link MessagingConfiguration} が担当する。
 * RabbitMQ への接続が利用できない場合は警告ログを出力してスキップする。
 */
public class RabbitMqCargoEventPublisher implements CargoEventPublisher {

    private static final Logger log = LoggerFactory.getLogger(RabbitMqCargoEventPublisher.class);

    static final String EXCHANGE = "cargo.events";
    static final String ROUTING_KEY_CARGO_ROUTED = "cargo.routed";
    static final String ROUTING_KEY_CARGO_ASSIGNED_FOR_ROUTING = "cargo.assigned.routing";

    private final RabbitTemplate rabbitTemplate;

    public RabbitMqCargoEventPublisher(RabbitTemplate rabbitTemplate) {
        this.rabbitTemplate = rabbitTemplate;
    }

    @Override
    public void publishCargoRouted(CargoRoutedEvent event) {
        try {
            rabbitTemplate.convertAndSend(EXCHANGE, ROUTING_KEY_CARGO_ROUTED, event);
        } catch (AmqpConnectException e) {
            log.warn("RabbitMQ unavailable, skipping publishCargoRouted: {}", e.getMessage());
        }
    }

    @Override
    public void publishCargoAssignedForRouting(CargoAssignedForRoutingEvent event) {
        try {
            rabbitTemplate.convertAndSend(EXCHANGE, ROUTING_KEY_CARGO_ASSIGNED_FOR_ROUTING, event);
        } catch (AmqpConnectException e) {
            log.warn("RabbitMQ unavailable, skipping publishCargoAssignedForRouting: {}", e.getMessage());
        }
    }
}
