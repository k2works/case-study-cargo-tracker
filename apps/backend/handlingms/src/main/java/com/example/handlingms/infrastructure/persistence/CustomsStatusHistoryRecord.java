package com.example.handlingms.infrastructure.persistence;

import java.time.Instant;

/** {@code customs_status_history} の 1 行。**追記しかしない**。 */
public class CustomsStatusHistoryRecord {

    private Long id;
    private Long customsDeclarationId;
    private String fromStatus;
    private String toStatus;
    private String changedBy;
    private Instant changedAt;
    private String reason;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getCustomsDeclarationId() {
        return customsDeclarationId;
    }

    public void setCustomsDeclarationId(Long customsDeclarationId) {
        this.customsDeclarationId = customsDeclarationId;
    }

    public String getFromStatus() {
        return fromStatus;
    }

    public void setFromStatus(String fromStatus) {
        this.fromStatus = fromStatus;
    }

    public String getToStatus() {
        return toStatus;
    }

    public void setToStatus(String toStatus) {
        this.toStatus = toStatus;
    }

    public String getChangedBy() {
        return changedBy;
    }

    public void setChangedBy(String changedBy) {
        this.changedBy = changedBy;
    }

    public Instant getChangedAt() {
        return changedAt;
    }

    public void setChangedAt(Instant changedAt) {
        this.changedAt = changedAt;
    }

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }
}
