package com.example.trackingms.infrastructure.messaging;

import com.example.trackingms.application.internal.NoteCancellationUseCase;
import org.springframework.amqp.rabbit.annotation.RabbitListener;

/**
 * 「キャンセルが確定した」を受け取って、荷主のお知らせに残す（[ADR-025] 決定 3）。
 *
 * <p><strong>ここだけがメッセージ基盤を知る</strong>
 * （`eventPublishingOnlyInMessagingInfrastructureRule` が検査する）。
 *
 * <p><strong>例外を握りつぶさない。</strong>握りつぶすと、受け取れなかったイベントが
 * 正常に処理されたことになり、デッドレターにも届かない（[ADR-022] 決定 4）。
 */
public class CargoCancelledListener {

    private final NoteCancellationUseCase noteCancellation;

    public CargoCancelledListener(NoteCancellationUseCase noteCancellation) {
        this.noteCancellation = noteCancellation;
    }

    @RabbitListener(queues = TrackingEventChannels.CANCELLED_QUEUE)
    public void onCargoCancelled(CargoCancelledMessage message) {
        noteCancellation.note(message.trackingNumber());
    }
}
