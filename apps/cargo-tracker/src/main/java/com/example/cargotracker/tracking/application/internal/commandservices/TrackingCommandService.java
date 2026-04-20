package com.example.cargotracker.tracking.application.internal.commandservices;

import com.example.cargotracker.tracking.domain.model.aggregates.TrackingRecord;
import com.example.cargotracker.tracking.domain.model.entities.TrackingActivityEvent;
import com.example.cargotracker.tracking.domain.model.repository.TrackingRepository;
import com.example.cargotracker.tracking.domain.model.valueobjects.TrackingBookingId;
import com.example.cargotracker.tracking.domain.model.valueobjects.TrackingNumber;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TrackingCommandService {

    private final TrackingRepository trackingRepository;

    public TrackingCommandService(TrackingRepository trackingRepository) {
        this.trackingRepository = trackingRepository;
    }

    @Transactional
    public TrackingNumber issueTrackingNumber(IssueTrackingNumberCommand command) {
        TrackingBookingId bookingId = TrackingBookingId.of(command.bookingId());
        TrackingNumber trackingNumber = TrackingNumber.generate();
        TrackingRecord trackingRecord = new TrackingRecord(trackingNumber, bookingId);
        trackingRepository.save(trackingRecord);
        return trackingNumber;
    }

    @Transactional
    public void recordHandlingEvent(RecordHandlingEventCommand command) {
        TrackingNumber trackingNumber = TrackingNumber.of(command.trackingNumber());
        TrackingRecord trackingRecord = trackingRepository.findByTrackingNumber(trackingNumber)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Tracking record not found: " + command.trackingNumber()));
        TrackingActivityEvent event = new TrackingActivityEvent(
                command.eventType(),
                command.locationUnlocode(),
                command.completionTime(),
                command.voyageNumber()
        );
        trackingRecord.addHandlingEvent(event);
        trackingRepository.updateStatus(trackingRecord);
        trackingRepository.saveHandlingEvent(command.trackingNumber(), event);
    }

    @Transactional
    public void applyManualStatusUpdate(ManualStatusUpdateCommand command) {
        TrackingNumber trackingNumber = TrackingNumber.of(command.trackingNumber());
        TrackingRecord trackingRecord = trackingRepository.findByTrackingNumber(trackingNumber)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Tracking record not found: " + command.trackingNumber()));
        trackingRecord.addManualUpdateEvent(command.newStatus(), command.locationUnlocode(), command.updateTime());
        trackingRepository.updateStatus(trackingRecord);
        TrackingActivityEvent lastEvent = trackingRecord.getHandlingEvents().getLast();
        trackingRepository.saveHandlingEvent(command.trackingNumber(), lastEvent);
    }
}
