package com.example.cargotracker.tracking.domain.model;

import com.example.cargotracker.tracking.domain.model.aggregates.TrackingRecord;
import com.example.cargotracker.tracking.domain.model.valueobjects.CargoTrackingStatus;
import com.example.cargotracker.tracking.domain.model.valueobjects.TrackingBookingId;
import com.example.cargotracker.tracking.domain.model.valueobjects.TrackingNumber;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

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
}
