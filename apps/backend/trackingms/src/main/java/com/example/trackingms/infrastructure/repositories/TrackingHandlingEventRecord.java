package com.example.trackingms.infrastructure.repositories;

import java.time.LocalDateTime;

/**
 * tracking_handling_event テーブルの MyBatis レコード
 */
public class TrackingHandlingEventRecord {
    private Long id;
    private Long trackingId;
    private String eventType;
    private LocalDateTime eventTime;
    private String locationUnlocode;
    private String voyageNumber;
    private String consigneeConfirmation;

    public TrackingHandlingEventRecord() {}

    public TrackingHandlingEventRecord(
            Long trackingId,
            String eventType,
            LocalDateTime eventTime,
            String locationUnlocode,
            String voyageNumber,
            String consigneeConfirmation) {
        this.trackingId = trackingId;
        this.eventType = eventType;
        this.eventTime = eventTime;
        this.locationUnlocode = locationUnlocode;
        this.voyageNumber = voyageNumber;
        this.consigneeConfirmation = consigneeConfirmation;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getTrackingId() { return trackingId; }
    public void setTrackingId(Long trackingId) { this.trackingId = trackingId; }

    public String getEventType() { return eventType; }
    public void setEventType(String eventType) { this.eventType = eventType; }

    public LocalDateTime getEventTime() { return eventTime; }
    public void setEventTime(LocalDateTime eventTime) { this.eventTime = eventTime; }

    public String getLocationUnlocode() { return locationUnlocode; }
    public void setLocationUnlocode(String locationUnlocode) { this.locationUnlocode = locationUnlocode; }

    public String getVoyageNumber() { return voyageNumber; }
    public void setVoyageNumber(String voyageNumber) { this.voyageNumber = voyageNumber; }

    public String getConsigneeConfirmation() { return consigneeConfirmation; }
    public void setConsigneeConfirmation(String consigneeConfirmation) { this.consigneeConfirmation = consigneeConfirmation; }
}
