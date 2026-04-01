package com.example.cargotracker.booking.infrastructure.event;

import com.example.cargotracker.booking.domain.event.BookingRegisteredEvent;
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
        // TODO: 経路設計者への通知は IT3 で実装
    }
}
