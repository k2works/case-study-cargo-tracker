package com.example.cargotracker.tracking.domain.model.repository;

import com.example.cargotracker.tracking.domain.model.aggregates.TrackingRecord;
import com.example.cargotracker.tracking.domain.model.entities.TrackingActivityEvent;
import com.example.cargotracker.tracking.domain.model.valueobjects.ExceptionType;
import com.example.cargotracker.tracking.domain.model.valueobjects.TrackingBookingId;
import com.example.cargotracker.tracking.domain.model.valueobjects.TrackingNumber;

import java.time.LocalDateTime;
import java.util.Optional;

public interface TrackingRepository {

    void save(TrackingRecord trackingRecord);

    Optional<TrackingRecord> findByTrackingNumber(TrackingNumber trackingNumber);

    Optional<TrackingRecord> findByBookingId(TrackingBookingId bookingId);

    void updateStatus(TrackingRecord trackingRecord);

    void saveHandlingEvent(String trackingNumber, TrackingActivityEvent event);

    void saveExceptionEvent(String trackingNumber, ExceptionType exceptionType,
                            String locationUnlocode, LocalDateTime occurrenceTime,
                            String reason, String responseNote, boolean escalationFlag);
}
