package com.example.trackingms.domain.projections;

import java.time.LocalDateTime;

/**
 * 追跡例外の Read Model（US19 / US20 / IT6 タスク 2.3）。
 *
 * <p>{@code tracking_exception} テーブル（data-model.md L564-578）を 1:1 で射影する。
 * 集約 {@link com.example.trackingms.domain.model.TrackingException} とは別に、クエリ最適化された
 * フラットな POJO として保持する。Controller のレスポンス DTO ではなく内部投影モデル。</p>
 */
public class TrackingExceptionView {
    private String exceptionId;
    private String trackingNumber;
    private String exceptionType;       // DELAY / DAMAGE / LOSS
    private LocalDateTime occurredAt;
    private String occurredUnlocode;
    private String description;
    private String responseStatus;      // REPORTED / RESPONDING / RESOLVED
    private String resolution;
    private LocalDateTime resolvedAt;
    private boolean escalated;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public String getExceptionId() { return exceptionId; }
    public void setExceptionId(String exceptionId) { this.exceptionId = exceptionId; }

    public String getTrackingNumber() { return trackingNumber; }
    public void setTrackingNumber(String trackingNumber) { this.trackingNumber = trackingNumber; }

    public String getExceptionType() { return exceptionType; }
    public void setExceptionType(String exceptionType) { this.exceptionType = exceptionType; }

    public LocalDateTime getOccurredAt() { return occurredAt; }
    public void setOccurredAt(LocalDateTime occurredAt) { this.occurredAt = occurredAt; }

    public String getOccurredUnlocode() { return occurredUnlocode; }
    public void setOccurredUnlocode(String occurredUnlocode) { this.occurredUnlocode = occurredUnlocode; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public String getResponseStatus() { return responseStatus; }
    public void setResponseStatus(String responseStatus) { this.responseStatus = responseStatus; }

    public String getResolution() { return resolution; }
    public void setResolution(String resolution) { this.resolution = resolution; }

    public LocalDateTime getResolvedAt() { return resolvedAt; }
    public void setResolvedAt(LocalDateTime resolvedAt) { this.resolvedAt = resolvedAt; }

    public boolean isEscalated() { return escalated; }
    public void setEscalated(boolean escalated) { this.escalated = escalated; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
