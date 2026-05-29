package com.example.trackingms.domain.projections;

import java.time.LocalDateTime;

/**
 * 追跡イベント履歴 Read Model (POJO + MyBatis ResultMap)（US17 / IT5 2.3）。
 *
 * <p>tracking_event テーブル（時系列・挿入のみ）の各カラムに対応するフィールド。</p>
 */
public class TrackingEvent {

    private Long eventId;
    private String trackingNumber;
    private LocalDateTime occurredAt;
    private LocalDateTime recordedAt;
    private String eventType;
    private String transportStatus;
    private String unlocode;
    private String voyageNumber;
    private String handlingType;
    private String source;
    private String description;

    public TrackingEvent() { /* MyBatis result mapping */ }

    public Long getEventId() { return eventId; }
    public void setEventId(Long eventId) { this.eventId = eventId; }

    public String getTrackingNumber() { return trackingNumber; }
    public void setTrackingNumber(String trackingNumber) { this.trackingNumber = trackingNumber; }

    public LocalDateTime getOccurredAt() { return occurredAt; }
    public void setOccurredAt(LocalDateTime occurredAt) { this.occurredAt = occurredAt; }

    public LocalDateTime getRecordedAt() { return recordedAt; }
    public void setRecordedAt(LocalDateTime recordedAt) { this.recordedAt = recordedAt; }

    public String getEventType() { return eventType; }
    public void setEventType(String eventType) { this.eventType = eventType; }

    public String getTransportStatus() { return transportStatus; }
    public void setTransportStatus(String transportStatus) { this.transportStatus = transportStatus; }

    public String getUnlocode() { return unlocode; }
    public void setUnlocode(String unlocode) { this.unlocode = unlocode; }

    public String getVoyageNumber() { return voyageNumber; }
    public void setVoyageNumber(String voyageNumber) { this.voyageNumber = voyageNumber; }

    public String getHandlingType() { return handlingType; }
    public void setHandlingType(String handlingType) { this.handlingType = handlingType; }

    public String getSource() { return source; }
    public void setSource(String source) { this.source = source; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
}
