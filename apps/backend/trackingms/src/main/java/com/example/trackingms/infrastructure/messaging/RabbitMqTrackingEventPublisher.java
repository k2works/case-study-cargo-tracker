package com.example.trackingms.infrastructure.messaging;

import com.example.trackingms.domain.events.TrackingNumberIssuedEvent;
import com.example.trackingms.domain.ports.TrackingEventPublisher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.AmqpConnectException;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

/**
 * RabbitMQ を用いた TrackingEventPublisher 実装
 *
 * <p>Bean 登録は {@link TrackingMessagingConfiguration} が担当する。
 * RabbitMQ への接続が利用できない場合は警告ログを出力してスキップする。
 */
public class RabbitMqTrackingEventPublisher implements TrackingEventPublisher {

    private static final Logger log = LoggerFactory.getLogger(RabbitMqTrackingEventPublisher.class);

    static final String EXCHANGE = "tracking.events";
    static final String ROUTING_KEY_TRACKING_NUMBER_ISSUED = "tracking.number.issued";

    private final RabbitTemplate rabbitTemplate;

    public RabbitMqTrackingEventPublisher(RabbitTemplate rabbitTemplate) {
        this.rabbitTemplate = rabbitTemplate;
    }

    @Override
    public void publishTrackingNumberIssued(TrackingNumberIssuedEvent event) {
        try {
            rabbitTemplate.convertAndSend(EXCHANGE, ROUTING_KEY_TRACKING_NUMBER_ISSUED, event);
        } catch (AmqpConnectException e) {
            log.warn("RabbitMQ unavailable, skipping publishTrackingNumberIssued: {}", e.getMessage());
        }
    }
}
