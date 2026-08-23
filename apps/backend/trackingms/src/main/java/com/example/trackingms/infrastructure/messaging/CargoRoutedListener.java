package com.example.trackingms.infrastructure.messaging;

import com.example.trackingms.application.internal.ApplyEstimatedArrivalUseCase;
import org.springframework.amqp.rabbit.annotation.RabbitListener;

/**
 * 「経路が決まった」を受け取って、到着の見込みを持つ（US18-2・[ADR-024] 決定 4）。
 *
 * <p><strong>ここだけがメッセージ基盤を知る</strong>
 * （`eventPublishingOnlyInMessagingInfrastructureRule` が検査する）。
 *
 * <p><strong>例外を握りつぶさない。</strong>握りつぶすと、受け取れなかったイベントが
 * 正常に処理されたことになり、デッドレターにも届かない（[ADR-022] 決定 4）。
 */
public class CargoRoutedListener {

    private final ApplyEstimatedArrivalUseCase applyEstimatedArrival;

    public CargoRoutedListener(ApplyEstimatedArrivalUseCase applyEstimatedArrival) {
        this.applyEstimatedArrival = applyEstimatedArrival;
    }

    @RabbitListener(queues = TrackingEventChannels.CARGO_ROUTED_QUEUE)
    public void onCargoRouted(CargoRoutedMessage message) {
        applyEstimatedArrival.apply(message.trackingNumber(), message.estimatedArrival());
    }
}
