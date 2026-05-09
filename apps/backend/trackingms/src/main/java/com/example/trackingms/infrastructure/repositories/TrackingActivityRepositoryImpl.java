package com.example.trackingms.infrastructure.repositories;

import com.example.trackingms.domain.model.aggregates.TrackingActivity;
import com.example.trackingms.domain.model.aggregates.TrackingActivityEvent;
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

    public TrackingActivityRepositoryImpl(TrackingActivityMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public TrackingActivity save(TrackingActivity activity) {
        TrackingActivityRecord activityRecord = new TrackingActivityRecord(
                activity.getTrackingNumber().number(),
                activity.getBookingId().bookingId(),
                activity.getTransportStatus().name()
        );
        mapper.insert(activityRecord);

        // イベントを保存
        for (TrackingActivityEvent event : activity.getEvents()) {
            TrackingHandlingEventRecord eventRecord = new TrackingHandlingEventRecord(
                    activityRecord.getId(),
                    event.getEventType().name(),
                    event.getEventTime(),
                    event.getLocationUnlocode(),
                    event.getVoyageNumber()
            );
            mapper.insertEvent(eventRecord);
        }

        return toEntity(activityRecord, List.of());
    }

    @Override
    public Optional<TrackingActivity> findByTrackingNumber(TrackingNumber trackingNumber) {
        return mapper.findByTrackingNumber(trackingNumber.number())
                .map(activityRecord -> {
                    List<TrackingHandlingEventRecord> eventRecords =
                            mapper.findEventsByTrackingId(activityRecord.getId());
                    return toEntity(activityRecord, eventRecords);
                });
    }

    @Override
    public Optional<TrackingActivity> findByBookingId(TrackingBookingId bookingId) {
        return mapper.findByBookingId(bookingId.bookingId())
                .map(activityRecord -> {
                    List<TrackingHandlingEventRecord> eventRecords =
                            mapper.findEventsByTrackingId(activityRecord.getId());
                    return toEntity(activityRecord, eventRecords);
                });
    }

    @Override
    public void update(TrackingActivity activity) {
        mapper.updateStatus(activity.getId(), activity.getTransportStatus().name());

        // 新しいイベントを保存（追記のみ）
        for (TrackingActivityEvent event : activity.getEvents()) {
            if (event.getId() == null) {
                TrackingHandlingEventRecord eventRecord = new TrackingHandlingEventRecord(
                        activity.getId(),
                        event.getEventType().name(),
                        event.getEventTime(),
                        event.getLocationUnlocode(),
                        event.getVoyageNumber()
                );
                mapper.insertEvent(eventRecord);
            }
        }
    }

    @Override
    public long nextTrackingNumberSequence() {
        return mapper.nextTrackingNumberSequence();
    }

    private TrackingActivity toEntity(TrackingActivityRecord activityRecord,
                                      List<TrackingHandlingEventRecord> eventRecords) {
        List<TrackingActivityEvent> events = eventRecords.stream()
                .map(e -> new TrackingActivityEvent(
                        e.getId(),
                        TrackingEventType.valueOf(e.getEventType()),
                        e.getLocationUnlocode(),
                        e.getEventTime(),
                        e.getVoyageNumber()))
                .toList();

        return new TrackingActivity(
                activityRecord.getId(),
                new TrackingNumber(activityRecord.getTrackingNumber()),
                new TrackingBookingId(activityRecord.getBookingId()),
                TrackingStatus.valueOf(activityRecord.getTransportStatus()),
                events
        );
    }
}
