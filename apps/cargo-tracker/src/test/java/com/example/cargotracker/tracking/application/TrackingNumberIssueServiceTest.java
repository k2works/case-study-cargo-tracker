package com.example.cargotracker.tracking.application;

import com.example.cargotracker.booking.domain.event.BookingConfirmedEvent;
import com.example.cargotracker.booking.domain.model.aggregates.BookingId;
import com.example.cargotracker.tracking.application.internal.commandservices.TrackingNumberIssueService;
import com.example.cargotracker.tracking.domain.repository.TrackingRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

import static org.assertj.core.api.Assertions.*;

@SpringBootTest
@Transactional
class TrackingNumberIssueServiceTest {

    @Autowired
    private TrackingNumberIssueService trackingNumberIssueService;

    @Autowired
    private TrackingRepository trackingRepository;

    @Test
    @DisplayName("予約確定イベント受信時に追跡番号が発行される")
    void issueTrackingNumberOnBookingConfirmed() {
        UUID bookingId = UUID.randomUUID();
        BookingConfirmedEvent event = new BookingConfirmedEvent(new BookingId(bookingId));

        trackingNumberIssueService.on(event);

        var entry = trackingRepository.findByBookingId(bookingId);
        assertThat(entry).isPresent();
        assertThat(entry.get().getTrackingNumber().value()).matches("TRK-[A-Z0-9]{8}");
    }

    @Test
    @DisplayName("同一予約への追跡番号は重複発行されない（冪等性）")
    void idempotentIssuance() {
        UUID bookingId = UUID.randomUUID();
        BookingConfirmedEvent event = new BookingConfirmedEvent(new BookingId(bookingId));

        trackingNumberIssueService.on(event);
        trackingNumberIssueService.on(event); // 2回目は何もしない

        // 例外が発生しないこと
        var entry = trackingRepository.findByBookingId(bookingId);
        assertThat(entry).isPresent();
    }
}
