package com.example.cargotracker.tracking.application.internal.commandservices;

import com.example.cargotracker.booking.domain.event.BookingConfirmedEvent;
import com.example.cargotracker.tracking.domain.model.aggregates.TrackingEntry;
import com.example.cargotracker.tracking.domain.model.valueobjects.TrackingNumber;
import com.example.cargotracker.tracking.domain.repository.TrackingRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.UUID;

@Service
public class TrackingNumberIssueService {

    private final TrackingRepository trackingRepository;

    public TrackingNumberIssueService(TrackingRepository trackingRepository) {
        this.trackingRepository = trackingRepository;
    }

    // ADR-002: 予約確定トランザクションの commit 後に追跡番号を発行する。
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void on(BookingConfirmedEvent event) {
        UUID bookingId = event.bookingId().value();
        // 冪等性：既に発行済みなら何もしない
        if (trackingRepository.findByBookingId(bookingId).isPresent()) {
            return;
        }
        TrackingNumber trackingNumber = TrackingNumber.generate();
        trackingRepository.save(new TrackingEntry(trackingNumber, bookingId));
    }
}
