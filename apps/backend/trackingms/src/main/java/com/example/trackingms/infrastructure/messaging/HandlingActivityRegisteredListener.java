package com.example.trackingms.infrastructure.messaging;

import com.example.trackingms.application.internal.commandservices.AdvanceTrackingUseCase;
import org.springframework.amqp.rabbit.annotation.RabbitListener;

/**
 * 「荷役作業を記録した」を受け取って追跡を進める（US15-4・[ADR-023] 決定 5）。
 *
 * <p><strong>ここだけがメッセージ基盤を知る</strong>
 * （`eventPublishingOnlyInMessagingInfrastructureRule` が検査する）。
 *
 * <p><strong>例外を握りつぶさない。</strong>握りつぶすと、受け取れなかったイベントが
 * 正常に処理されたことになり、デッドレターにも届かない。荷役は記録されているのに追跡が
 * 進まないまま、どこにも異常が残らない状態になる（[ADR-022] 決定 4）。
 */
public class HandlingActivityRegisteredListener {

    private final AdvanceTrackingUseCase advanceTracking;
    private final com.example.trackingms.application.internal.commandservices.DetectMisrouteUseCase detectMisroute;

    public HandlingActivityRegisteredListener(AdvanceTrackingUseCase advanceTracking,
            com.example.trackingms.application.internal.commandservices.DetectMisrouteUseCase detectMisroute) {
        this.advanceTracking = advanceTracking;
        this.detectMisroute = detectMisroute;
    }

    @RabbitListener(queues = TrackingEventChannels.HANDLING_QUEUE)
    public void onHandlingActivityRegistered(HandlingActivityRegisteredMessage message) {
        advanceTracking.advance(message.trackingNumber(), message.type(),
                message.locationUnLocode(), message.completionTime());

        // **先に状態を進めてから起票する**（US28-2）。順序を逆にすると、例外を起票した
        // 直後に荷役の状態で上書きされ、**未解決の例外が一覧から消える**。
        // 順序は `HandlingActivityRegisteredListenerTest` が固定している——
        // 逆にすると赤になる（コメントだけでは、逆順にしても何も落ちなかった）
        detectMisroute.onHandlingActivityRegistered(message.trackingNumber(),
                message.locationUnLocode(), message.completionTime(), message.offRoute());
    }
}
