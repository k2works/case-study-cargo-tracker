package com.example.trackingms.infrastructure.repositories;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * tracking_exception_event テーブルの永続化レコード
 */
public class TrackingExceptionEventRecord {

    private Long id;
    private Long trackingId;
    private String exceptionType;
    private LocalDateTime occurredAt;
    private String locationUnlocode;
    private String reason;
    private boolean escalationFlag;
    private String responseContent;
    private LocalDate newEstimatedArrival;
    private String status;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getTrackingId() { return trackingId; }
    public void setTrackingId(Long trackingId) { this.trackingId = trackingId; }

    public String getExceptionType() { return exceptionType; }
    public void setExceptionType(String exceptionType) { this.exceptionType = exceptionType; }

    public LocalDateTime getOccurredAt() { return occurredAt; }
    public void setOccurredAt(LocalDateTime occurredAt) { this.occurredAt = occurredAt; }

    public String getLocationUnlocode() { return locationUnlocode; }
    public void setLocationUnlocode(String locationUnlocode) { this.locationUnlocode = locationUnlocode; }

    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }

    public boolean isEscalationFlag() { return escalationFlag; }
    public void setEscalationFlag(boolean escalationFlag) { this.escalationFlag = escalationFlag; }

    public String getResponseContent() { return responseContent; }
    public void setResponseContent(String responseContent) { this.responseContent = responseContent; }

    public LocalDate getNewEstimatedArrival() { return newEstimatedArrival; }
    public void setNewEstimatedArrival(LocalDate newEstimatedArrival) { this.newEstimatedArrival = newEstimatedArrival; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
