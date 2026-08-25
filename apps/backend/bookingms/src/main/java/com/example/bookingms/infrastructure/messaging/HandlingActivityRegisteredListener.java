package com.example.bookingms.infrastructure.messaging;

import com.example.bookingms.application.internal.AdvanceBookingUseCase;
import org.springframework.amqp.rabbit.annotation.RabbitListener;

/**
 * 「荷役作業を記録した」を受け取って予約を進める（US30・[ADR-025] 決定 1）。
 *
 * <p><strong>ここだけがメッセージ基盤を知る</strong>
 * （`eventPublishingOnlyInMessagingInfrastructureRule` が検査する）。
 *
 * <p><strong>例外を握りつぶさない。</strong>握りつぶすと、受け取れなかったイベントが
 * 正常に処理されたことになり、デッドレターにも届かない（[ADR-022] 決定 4）。
 */
public class HandlingActivityRegisteredListener {

    private final AdvanceBookingUseCase advanceBooking;

    public HandlingActivityRegisteredListener(AdvanceBookingUseCase advanceBooking) {
        this.advanceBooking = advanceBooking;
    }

    @RabbitListener(queues = CargoEventChannels.HANDLING_QUEUE)
    public void onHandlingActivityRegistered(HandlingActivityRegisteredMessage message) {
        // **offRoute はイベントが運ぶ**（[ADR-022] の契約に既にある・[ADR-026] 決定 1）。
        // 新しいイベントを作らない——交換機を増やすほど移行の手順が要る
        advanceBooking.advance(message.trackingNumber(), message.type(),
                message.locationUnLocode(), message.completionTime(), message.offRoute());
    }
}
