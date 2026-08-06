package com.example.cargotracker.tracking.domain.model.aggregates;

import com.example.cargotracker.tracking.domain.model.entities.TrackingActivityEvent;
import com.example.cargotracker.tracking.domain.model.valueobjects.CargoTrackingStatus;
import com.example.cargotracker.tracking.domain.model.valueobjects.ExceptionType;
import com.example.cargotracker.tracking.domain.model.valueobjects.TrackingBookingId;
import com.example.cargotracker.tracking.domain.model.valueobjects.TrackingEventType;
import com.example.cargotracker.tracking.domain.model.valueobjects.TrackingNumber;

import java.time.LocalDateTime;
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
        TrackingRecord trackingRecord = new TrackingRecord(trackingNumber, bookingId);
        trackingRecord.status = status;
        return trackingRecord;
    }

    public void addHandlingEvent(TrackingActivityEvent event) {
        if (event == null) throw new IllegalArgumentException("event must not be null");
        handlingEvents.add(event);
        this.status = deriveStatus(event.getEventType());
    }

    public void addException(ExceptionType exceptionType, String locationUnlocode, LocalDateTime dateTime) {
        if (exceptionType == null) throw new IllegalArgumentException("exceptionType must not be null");
        if (locationUnlocode == null || locationUnlocode.isBlank()) throw new IllegalArgumentException("locationUnlocode must not be blank");
        if (dateTime == null) throw new IllegalArgumentException("dateTime must not be null");
        TrackingActivityEvent event = new TrackingActivityEvent(
                TrackingEventType.EXCEPTION, locationUnlocode, dateTime, null);
        handlingEvents.add(event);
        this.status = CargoTrackingStatus.EXCEPTION;
    }

    public void addManualUpdateEvent(CargoTrackingStatus newStatus, String locationUnlocode, LocalDateTime dateTime) {
        if (newStatus == null) throw new IllegalArgumentException("newStatus must not be null");
        if (locationUnlocode == null || locationUnlocode.isBlank()) throw new IllegalArgumentException("locationUnlocode must not be blank");
        if (dateTime == null) throw new IllegalArgumentException("dateTime must not be null");
        TrackingActivityEvent event = new TrackingActivityEvent(
                TrackingEventType.MANUAL_UPDATE, locationUnlocode, dateTime, null);
        handlingEvents.add(event);
        this.status = newStatus;
    }

    private CargoTrackingStatus deriveStatus(TrackingEventType eventType) {
        return switch (eventType) {
            case RECEIVE -> CargoTrackingStatus.RECEIVED;
            case LOAD -> CargoTrackingStatus.LOADED;
            case UNLOAD -> CargoTrackingStatus.UNLOADED;
            case CLAIM -> CargoTrackingStatus.CLAIMED;
            case MANUAL_UPDATE -> this.status;
            case EXCEPTION -> CargoTrackingStatus.EXCEPTION;
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
