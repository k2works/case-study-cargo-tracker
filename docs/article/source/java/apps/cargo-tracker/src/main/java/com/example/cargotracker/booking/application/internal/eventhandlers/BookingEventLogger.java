package com.example.cargotracker.booking.application.internal.eventhandlers;

import com.example.cargotracker.booking.domain.model.events.BookingAssignedToRoutingEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Component
public class BookingEventLogger {

    private static final Logger log = LoggerFactory.getLogger(BookingEventLogger.class);

    @EventListener
    public void onBookingAssignedToRouting(BookingAssignedToRoutingEvent event) {
        log.info("[通知スタブ] 予約 {} を経路設計者に引き渡しました。", event.bookingId());
    }
}
