package com.example.trackingms.infrastructure.messaging;

import com.example.trackingms.application.internal.commandservices.StartTrackingUseCase;
import org.springframework.amqp.rabbit.annotation.RabbitListener;

/**
 * 「追跡番号を発行した」を受け取って追跡を始める（US14-3・[ADR-022]）。
 *
 * <p><strong>ここだけがメッセージ基盤を知る</strong>
 * （`eventPublishingOnlyInMessagingInfrastructureRule` が検査する）。
 *
 * <p><strong>例外を握りつぶさない。</strong>握りつぶすと、受け取れなかったイベントが
 * 正常に処理されたことになり、デッドレターにも届かない。追跡が作られないまま、
 * どこにも異常が残らない状態になる（[ADR-022] 決定 4）。
 */
public class TrackingNumberIssuedListener {

    private final StartTrackingUseCase startTracking;

    public TrackingNumberIssuedListener(StartTrackingUseCase startTracking) {
        this.startTracking = startTracking;
    }

    @RabbitListener(queues = TrackingEventChannels.QUEUE)
    public void onTrackingNumberIssued(TrackingNumberIssuedMessage message) {
        startTracking.start(message.trackingNumber(), message.bookingId(),
                message.originUnLocode(), message.destinationUnLocode(),
                message.arrivalDeadline(), message.estimatedArrival());
    }
}
