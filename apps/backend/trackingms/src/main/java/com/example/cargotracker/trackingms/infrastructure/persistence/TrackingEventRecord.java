package com.example.cargotracker.trackingms.infrastructure.persistence;

import java.time.LocalDateTime;

/**
 * tracking_event テーブルの Read Model レコード（MyBatis 用 POJO）。
 *
 * <p>変数名規約: 本クラスの参照変数は {@code event} を使用する（IT5 ふりかえり T3）。</p>
 */
public class TrackingEventRecord {

    private Long eventId;
    private String trackingNumber;
    private LocalDateTime occurredAt;
    private LocalDateTime recordedAt;
    private String eventType;
    private String transportStatus;
    private String unlocode;
    private String voyageNumber;
    private String handlingType;
    private String description;
    private String source;

    public TrackingEventRecord() {
        // MyBatis default constructor
    }

    public TrackingEventRecord(
            Long eventId,
            String trackingNumber,
            LocalDateTime occurredAt,
            LocalDateTime recordedAt,
            String eventType,
            String transportStatus,
            String unlocode,
            String voyageNumber,
            String handlingType,
            String description,
            String source) {
        this.eventId = eventId;
        this.trackingNumber = trackingNumber;
        this.occurredAt = occurredAt;
        this.recordedAt = recordedAt;
        this.eventType = eventType;
        this.transportStatus = transportStatus;
        this.unlocode = unlocode;
        this.voyageNumber = voyageNumber;
        this.handlingType = handlingType;
        this.description = description;
        this.source = source;
    }

    public Long eventId() {
        return eventId;
    }

    public String trackingNumber() {
        return trackingNumber;
    }

    public LocalDateTime occurredAt() {
        return occurredAt;
    }

    public LocalDateTime recordedAt() {
        return recordedAt;
    }

    public String eventType() {
        return eventType;
    }

    public String transportStatus() {
        return transportStatus;
    }

    public String unlocode() {
        return unlocode;
    }

    public String voyageNumber() {
        return voyageNumber;
    }

    public String handlingType() {
        return handlingType;
    }

    public String description() {
        return description;
    }

    public String source() {
        return source;
    }

    public Long getEventId() {
        return eventId;
    }

    public void setEventId(Long eventId) {
        this.eventId = eventId;
    }

    public String getTrackingNumber() {
        return trackingNumber;
    }

    public void setTrackingNumber(String trackingNumber) {
        this.trackingNumber = trackingNumber;
    }

    public LocalDateTime getOccurredAt() {
        return occurredAt;
    }

    public void setOccurredAt(LocalDateTime occurredAt) {
        this.occurredAt = occurredAt;
    }

    public LocalDateTime getRecordedAt() {
        return recordedAt;
    }

    public void setRecordedAt(LocalDateTime recordedAt) {
        this.recordedAt = recordedAt;
    }

    public String getEventType() {
        return eventType;
    }

    public void setEventType(String eventType) {
        this.eventType = eventType;
    }

    public String getTransportStatus() {
        return transportStatus;
    }

    public void setTransportStatus(String transportStatus) {
        this.transportStatus = transportStatus;
    }

    public String getUnlocode() {
        return unlocode;
    }

    public void setUnlocode(String unlocode) {
        this.unlocode = unlocode;
    }

    public String getVoyageNumber() {
        return voyageNumber;
    }

    public void setVoyageNumber(String voyageNumber) {
        this.voyageNumber = voyageNumber;
    }

    public String getHandlingType() {
        return handlingType;
    }

    public void setHandlingType(String handlingType) {
        this.handlingType = handlingType;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getSource() {
        return source;
    }

    public void setSource(String source) {
        this.source = source;
    }
}
