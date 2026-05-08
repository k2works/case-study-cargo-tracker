package com.example.trackingms.domain.model.aggregates;

import com.example.trackingms.domain.model.valueobjects.TrackingEventType;

import java.time.LocalDateTime;
import java.util.Objects;

/**
 * 追跡イベント（エンティティ）
 */
public class TrackingActivityEvent {
    private Long id;
    private final TrackingEventType eventType;
    private final String locationUnlocode;
    private final LocalDateTime eventTime;
    private final String voyageNumber;

    public TrackingActivityEvent(TrackingEventType eventType,
                                  String locationUnlocode,
                                  LocalDateTime eventTime,
                                  String voyageNumber) {
        Objects.requireNonNull(eventType, "eventType must not be null");
        Objects.requireNonNull(locationUnlocode, "locationUnlocode must not be null");
        Objects.requireNonNull(eventTime, "eventTime must not be null");
        this.eventType = eventType;
        this.locationUnlocode = locationUnlocode;
        this.eventTime = eventTime;
        this.voyageNumber = voyageNumber;
    }

    /** 永続化済みエンティティ再構成コンストラクタ */
    public TrackingActivityEvent(Long id, TrackingEventType eventType,
                                  String locationUnlocode,
                                  LocalDateTime eventTime,
                                  String voyageNumber) {
        this(eventType, locationUnlocode, eventTime, voyageNumber);
        this.id = id;
    }

    public Long getId() { return id; }
    public TrackingEventType getEventType() { return eventType; }
    public String getLocationUnlocode() { return locationUnlocode; }
    public LocalDateTime getEventTime() { return eventTime; }
    public String getVoyageNumber() { return voyageNumber; }
}
