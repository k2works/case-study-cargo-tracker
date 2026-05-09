package com.example.trackingms.infrastructure.messaging;

import com.example.trackingms.domain.events.TrackingNumberIssuedEvent;
import com.example.trackingms.domain.ports.TrackingEventPublisher;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

/**
 * RabbitMQ を用いた TrackingEventPublisher 実装
 *
 * <p>Bean 登録は {@link TrackingMessagingConfiguration} が担当する。
 */
public class RabbitMqTrackingEventPublisher implements TrackingEventPublisher {

    static final String EXCHANGE = "tracking.events";
    static final String ROUTING_KEY_TRACKING_NUMBER_ISSUED = "tracking.number.issued";

    private final RabbitTemplate rabbitTemplate;

    public RabbitMqTrackingEventPublisher(RabbitTemplate rabbitTemplate) {
        this.rabbitTemplate = rabbitTemplate;
    }

    @Override
    public void publishTrackingNumberIssued(TrackingNumberIssuedEvent event) {
        rabbitTemplate.convertAndSend(EXCHANGE, ROUTING_KEY_TRACKING_NUMBER_ISSUED, event);
    }
}
