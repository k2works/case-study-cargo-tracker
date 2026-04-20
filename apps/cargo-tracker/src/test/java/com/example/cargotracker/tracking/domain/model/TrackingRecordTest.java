package com.example.cargotracker.tracking.domain.model;

import com.example.cargotracker.tracking.domain.model.aggregates.TrackingRecord;
import com.example.cargotracker.tracking.domain.model.valueobjects.CargoTrackingStatus;
import com.example.cargotracker.tracking.domain.model.valueobjects.TrackingBookingId;
import com.example.cargotracker.tracking.domain.model.valueobjects.TrackingEventType;
import com.example.cargotracker.tracking.domain.model.valueobjects.TrackingNumber;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.assertj.core.api.Assertions.assertThat;

class TrackingRecordTest {

    private final TrackingNumber trackingNumber = TrackingNumber.of("TRK-20260417-ABCD1234");
    private final TrackingBookingId bookingId = TrackingBookingId.of(UUID.randomUUID().toString());

    @Test
    void shouldCreateTrackingRecordWithAwaitingReceiptStatus() {
        TrackingRecord record = new TrackingRecord(trackingNumber, bookingId);

        assertEquals(trackingNumber, record.getTrackingNumber());
        assertEquals(bookingId, record.getBookingId());
        assertEquals(CargoTrackingStatus.AWAITING_RECEIPT, record.getStatus());
    }

    @Test
    void shouldThrowWhenTrackingNumberIsNull() {
        assertThrows(IllegalArgumentException.class,
                () -> new TrackingRecord(null, bookingId));
    }

    @Test
    void shouldThrowWhenBookingIdIsNull() {
        assertThrows(IllegalArgumentException.class,
                () -> new TrackingRecord(trackingNumber, null));
    }

    @Test
    void 手動ステータス更新でステータスが変更されイベントが記録される() {
        TrackingRecord record = new TrackingRecord(trackingNumber, bookingId);
        LocalDateTime now = LocalDateTime.now();

        record.addManualUpdateEvent(CargoTrackingStatus.LOADED, "JPTYO", now);

        assertThat(record.getStatus()).isEqualTo(CargoTrackingStatus.LOADED);
        assertThat(record.getHandlingEvents()).hasSize(1);
        assertThat(record.getHandlingEvents().get(0).getEventType()).isEqualTo(TrackingEventType.MANUAL_UPDATE);
        assertThat(record.getHandlingEvents().get(0).getLocationUnlocode()).isEqualTo("JPTYO");
    }
}
