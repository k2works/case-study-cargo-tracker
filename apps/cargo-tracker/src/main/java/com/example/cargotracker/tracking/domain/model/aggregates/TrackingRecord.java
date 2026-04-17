package com.example.cargotracker.tracking.domain.model.aggregates;

import com.example.cargotracker.tracking.domain.model.valueobjects.CargoTrackingStatus;
import com.example.cargotracker.tracking.domain.model.valueobjects.TrackingBookingId;
import com.example.cargotracker.tracking.domain.model.valueobjects.TrackingNumber;

public class TrackingRecord {

    private final TrackingNumber trackingNumber;
    private final TrackingBookingId bookingId;
    private CargoTrackingStatus status;

    public TrackingRecord(TrackingNumber trackingNumber, TrackingBookingId bookingId) {
        if (trackingNumber == null) throw new IllegalArgumentException("trackingNumber must not be null");
        if (bookingId == null) throw new IllegalArgumentException("bookingId must not be null");
        this.trackingNumber = trackingNumber;
        this.bookingId = bookingId;
        this.status = CargoTrackingStatus.AWAITING_RECEIPT;
    }

    public static TrackingRecord reconstruct(
            TrackingNumber trackingNumber,
            TrackingBookingId bookingId,
            CargoTrackingStatus status
    ) {
        TrackingRecord record = new TrackingRecord(trackingNumber, bookingId);
        record.status = status;
        return record;
    }

    public TrackingNumber getTrackingNumber() {
        return trackingNumber;
    }

    public TrackingBookingId getBookingId() {
        return bookingId;
    }

    public CargoTrackingStatus getStatus() {
        return status;
    }
}
