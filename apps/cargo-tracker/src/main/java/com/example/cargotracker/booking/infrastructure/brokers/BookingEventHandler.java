package com.example.cargotracker.booking.infrastructure.brokers;

import com.example.cargotracker.booking.domain.event.BookingRegisteredEvent;
import com.example.cargotracker.booking.domain.event.BookingRouteAssignedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
public class BookingEventHandler {

    private static final Logger log = LoggerFactory.getLogger(BookingEventHandler.class);

    // cf. ADR-002: AFTER_COMMIT を使用することでロールバック時のサイドエフェクトを防止
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onBookingRegistered(BookingRegisteredEvent event) {
        log.info("予約登録イベントを受信しました: bookingId={}, shipperId={}",
                event.bookingId(), event.shipperId());
        // IT3 時点では予約登録後の外部通知要件は未実装。監査ログのみを記録する。
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onBookingRouteAssigned(BookingRouteAssignedEvent event) {
        log.info("経路確定イベントを受信しました: bookingId={}, voyageNumber={}",
                event.bookingId(), event.assignedRoute().voyageNumber());
        log.info("営業担当者への経路確定通知を送信しました: bookingId={}, routePath={}",
                event.bookingId(), event.assignedRoute().routePath());
        log.info("荷主への経路確定通知を送信しました: bookingId={}, 推定着日={}",
                event.bookingId(), event.assignedRoute().estimatedArrival());
    }
}
