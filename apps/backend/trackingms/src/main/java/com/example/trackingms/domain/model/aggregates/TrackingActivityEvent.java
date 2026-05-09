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
    /** 荷受人確認情報（CLAIM 種別のみ必須） */
    private final String consigneeConfirmation;

    public TrackingActivityEvent(TrackingEventType eventType,
                                  String locationUnlocode,
                                  LocalDateTime eventTime,
                                  String voyageNumber,
                                  String consigneeConfirmation) {
        Objects.requireNonNull(eventType, "eventType must not be null");
        Objects.requireNonNull(locationUnlocode, "locationUnlocode must not be null");
        Objects.requireNonNull(eventTime, "eventTime must not be null");
        this.eventType = eventType;
        this.locationUnlocode = locationUnlocode;
        this.eventTime = eventTime;
        this.voyageNumber = voyageNumber;
        this.consigneeConfirmation = consigneeConfirmation;
    }

    /** 永続化済みエンティティ再構成コンストラクタ */
    public TrackingActivityEvent(Long id, TrackingEventType eventType,
                                  String locationUnlocode,
                                  LocalDateTime eventTime,
                                  String voyageNumber,
                                  String consigneeConfirmation) {
        this(eventType, locationUnlocode, eventTime, voyageNumber, consigneeConfirmation);
        this.id = id;
    }

    public Long getId() { return id; }
    public TrackingEventType getEventType() { return eventType; }
    public String getLocationUnlocode() { return locationUnlocode; }
    public LocalDateTime getEventTime() { return eventTime; }
    public String getVoyageNumber() { return voyageNumber; }
    public String getConsigneeConfirmation() { return consigneeConfirmation; }
}
