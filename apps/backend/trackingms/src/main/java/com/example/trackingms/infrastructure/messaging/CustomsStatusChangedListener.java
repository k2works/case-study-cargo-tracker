package com.example.trackingms.infrastructure.messaging;

import com.example.trackingms.application.internal.DetectCustomsHoldUseCase;
import org.springframework.amqp.rabbit.annotation.RabbitListener;

/**
 * 「通関状態が変わった」を受け取って、留置を例外として起票する（US29-5）。
 *
 * <p><strong>ここだけがメッセージ基盤を知る</strong>
 * （`eventPublishingOnlyInMessagingInfrastructureRule` が検査する）。
 *
 * <p><strong>例外を握りつぶさない。</strong>握りつぶすと、留め置かれた貨物が誰の目にも
 * 入らないまま、どこにも異常が残らない（[ADR-022] 決定 4）。
 */
public class CustomsStatusChangedListener {

    private final DetectCustomsHoldUseCase detectCustomsHold;

    public CustomsStatusChangedListener(DetectCustomsHoldUseCase detectCustomsHold) {
        this.detectCustomsHold = detectCustomsHold;
    }

    @RabbitListener(queues = TrackingEventChannels.CUSTOMS_QUEUE)
    public void onCustomsStatusChanged(CustomsStatusChangedMessage message) {
        detectCustomsHold.onCustomsStatusChanged(message.trackingNumber(), message.toStatus(),
                message.reason(), message.changedAt());
    }
}
