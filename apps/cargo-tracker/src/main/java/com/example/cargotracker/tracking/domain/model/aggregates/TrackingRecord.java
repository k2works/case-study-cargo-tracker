package com.example.cargotracker.tracking.domain.model.aggregates;

import com.example.cargotracker.tracking.domain.model.entities.TrackingActivityEvent;
import com.example.cargotracker.tracking.domain.model.valueobjects.CargoTrackingStatus;
import com.example.cargotracker.tracking.domain.model.valueobjects.TrackingBookingId;
import com.example.cargotracker.tracking.domain.model.valueobjects.TrackingEventType;
import com.example.cargotracker.tracking.domain.model.valueobjects.TrackingNumber;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class TrackingRecord {

    private final TrackingNumber trackingNumber;
    private final TrackingBookingId bookingId;
    private CargoTrackingStatus status;
    private final List<TrackingActivityEvent> handlingEvents = new ArrayList<>();

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

    public void addHandlingEvent(TrackingActivityEvent event) {
        if (event == null) throw new IllegalArgumentException("event must not be null");
        handlingEvents.add(event);
        this.status = deriveStatus(event.getEventType());
    }

    private CargoTrackingStatus deriveStatus(TrackingEventType eventType) {
        return switch (eventType) {
            case RECEIVE -> CargoTrackingStatus.RECEIVED;
            case LOAD -> CargoTrackingStatus.LOADED;
            case UNLOAD -> CargoTrackingStatus.UNLOADED;
        };
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

    public List<TrackingActivityEvent> getHandlingEvents() {
        return Collections.unmodifiableList(handlingEvents);
    }
}
