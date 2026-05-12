package com.example.trackingms.infrastructure.repositories;

import com.example.trackingms.domain.model.aggregates.TrackingActivity;
import com.example.trackingms.domain.model.aggregates.TrackingActivityEvent;
import com.example.trackingms.domain.model.aggregates.TrackingExceptionEvent;
import com.example.trackingms.domain.model.valueobjects.ExceptionStatus;
import com.example.trackingms.domain.model.valueobjects.ExceptionType;
import com.example.trackingms.domain.model.valueobjects.TrackingBookingId;
import com.example.trackingms.domain.model.valueobjects.TrackingEventType;
import com.example.trackingms.domain.model.valueobjects.TrackingNumber;
import com.example.trackingms.domain.model.valueobjects.TrackingStatus;
import com.example.trackingms.domain.ports.TrackingActivityRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * TrackingActivityRepository の MyBatis 実装
 */
@Repository
public class TrackingActivityRepositoryImpl implements TrackingActivityRepository {

    private final TrackingActivityMapper mapper;
    private final TrackingExceptionEventMapper exceptionMapper;

    public TrackingActivityRepositoryImpl(TrackingActivityMapper mapper,
                                          TrackingExceptionEventMapper exceptionMapper) {
        this.mapper = mapper;
        this.exceptionMapper = exceptionMapper;
    }

    @Override
    public TrackingActivity save(TrackingActivity activity) {
        TrackingActivityRecord activityRecord = new TrackingActivityRecord(
                activity.getTrackingNumber().number(),
                activity.getBookingId().bookingId(),
                activity.getTransportStatus().name()
        );
        mapper.insert(activityRecord);

        for (TrackingActivityEvent event : activity.getEvents()) {
            TrackingHandlingEventRecord eventRecord = new TrackingHandlingEventRecord(
                    activityRecord.getId(),
                    event.getEventType().name(),
                    event.getEventTime(),
                    event.getLocationUnlocode(),
                    event.getVoyageNumber(),
                    event.getConsigneeConfirmation()
            );
            mapper.insertEvent(eventRecord);
        }

        for (TrackingExceptionEvent exception : activity.getExceptions()) {
            insertExceptionRecord(activityRecord.getId(), exception);
        }

        return toEntity(activityRecord, List.of(), List.of());
    }

    @Override
    public Optional<TrackingActivity> findByTrackingNumber(TrackingNumber trackingNumber) {
        return mapper.findByTrackingNumber(trackingNumber.number())
                .map(activityRecord -> {
                    List<TrackingHandlingEventRecord> eventRecords =
                            mapper.findEventsByTrackingId(activityRecord.getId());
                    List<TrackingExceptionEventRecord> exceptionRecords =
                            exceptionMapper.findExceptionsByTrackingId(activityRecord.getId());
                    return toEntity(activityRecord, eventRecords, exceptionRecords);
                });
    }

    @Override
    public Optional<TrackingActivity> findByBookingId(TrackingBookingId bookingId) {
        return mapper.findByBookingId(bookingId.bookingId())
                .map(activityRecord -> {
                    List<TrackingHandlingEventRecord> eventRecords =
                            mapper.findEventsByTrackingId(activityRecord.getId());
                    List<TrackingExceptionEventRecord> exceptionRecords =
                            exceptionMapper.findExceptionsByTrackingId(activityRecord.getId());
                    return toEntity(activityRecord, eventRecords, exceptionRecords);
                });
    }

    @Override
    public void update(TrackingActivity activity) {
        mapper.updateStatus(activity.getId(), activity.getTransportStatus().name());

        for (TrackingActivityEvent event : activity.getEvents()) {
            if (event.getId() == null) {
                TrackingHandlingEventRecord eventRecord = new TrackingHandlingEventRecord(
                        activity.getId(),
                        event.getEventType().name(),
                        event.getEventTime(),
                        event.getLocationUnlocode(),
                        event.getVoyageNumber(),
                        event.getConsigneeConfirmation()
                );
                mapper.insertEvent(eventRecord);
            }
        }

        for (TrackingExceptionEvent exception : activity.getExceptions()) {
            if (exception.getId() == null) {
                insertExceptionRecord(activity.getId(), exception);
            } else if (exception.hasBeenResponded()) {
                TrackingExceptionEventRecord responseRecord = new TrackingExceptionEventRecord();
                responseRecord.setId(exception.getId());
                responseRecord.setResponseContent(exception.getResponseContent());
                responseRecord.setNewEstimatedArrival(exception.getNewEstimatedArrival());
                responseRecord.setStatus(exception.getStatus().name());
                exceptionMapper.updateExceptionResponse(responseRecord);
            }
        }
    }

    @Override
    public long nextTrackingNumberSequence() {
        return mapper.nextTrackingNumberSequence();
    }

    private void insertExceptionRecord(Long trackingId, TrackingExceptionEvent exception) {
        TrackingExceptionEventRecord exceptionRecord = new TrackingExceptionEventRecord();
        exceptionRecord.setTrackingId(trackingId);
        exceptionRecord.setExceptionType(exception.getExceptionType().name());
        exceptionRecord.setOccurredAt(exception.getOccurredAt());
        exceptionRecord.setLocationUnlocode(exception.getLocationUnlocode());
        exceptionRecord.setReason(exception.getReason());
        exceptionRecord.setEscalationFlag(exception.isEscalationFlag());
        exceptionRecord.setResponseContent(exception.getResponseContent());
        exceptionRecord.setNewEstimatedArrival(exception.getNewEstimatedArrival());
        exceptionRecord.setStatus(exception.getStatus().name());
        exceptionRecord.setDamageDescription(exception.getDamageDescription());
        exceptionRecord.setPhotoUrl(exception.getPhotoUrl());
        exceptionRecord.setLastKnownLocation(exception.getLastKnownLocation());
        exceptionRecord.setLastSeenAt(exception.getLastSeenAt());
        exceptionMapper.insertException(exceptionRecord);
    }

    private TrackingActivity toEntity(TrackingActivityRecord activityRecord,
                                      List<TrackingHandlingEventRecord> eventRecords,
                                      List<TrackingExceptionEventRecord> exceptionRecords) {
        List<TrackingActivityEvent> events = eventRecords.stream()
                .map(e -> new TrackingActivityEvent(
                        e.getId(),
                        TrackingEventType.valueOf(e.getEventType()),
                        e.getLocationUnlocode(),
                        e.getEventTime(),
                        e.getVoyageNumber(),
                        e.getConsigneeConfirmation()))
                .toList();

        List<TrackingExceptionEvent> exceptions = exceptionRecords.stream()
                .map(e -> new TrackingExceptionEvent(
                        new TrackingExceptionEvent.PersistedState(
                                e.getId(), e.getResponseContent(),
                                e.getNewEstimatedArrival(), ExceptionStatus.valueOf(e.getStatus())),
                        ExceptionType.valueOf(e.getExceptionType()),
                        e.getOccurredAt(),
                        e.getLocationUnlocode(),
                        e.getReason(),
                        e.isEscalationFlag(),
                        new TrackingExceptionEvent.ExtendedDetails(
                                e.getDamageDescription(), e.getPhotoUrl(),
                                e.getLastKnownLocation(), e.getLastSeenAt())))
                .toList();

        return new TrackingActivity(
                activityRecord.getId(),
                new TrackingNumber(activityRecord.getTrackingNumber()),
                new TrackingBookingId(activityRecord.getBookingId()),
                TrackingStatus.valueOf(activityRecord.getTransportStatus()),
                events,
                exceptions
        );
    }
}
