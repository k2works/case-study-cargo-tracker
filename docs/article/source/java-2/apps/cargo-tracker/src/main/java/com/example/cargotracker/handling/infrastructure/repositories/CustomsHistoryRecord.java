package com.example.cargotracker.handling.infrastructure.repositories;

import java.time.Instant;

/** 通関状態の変更履歴レコード（US29）。 */
public class CustomsHistoryRecord {

    private long declarationId;
    private String statusFrom;
    private String statusTo;
    private String reason;
    private String changedBy;
    private Instant changedAt;

    public long getDeclarationId() {
        return declarationId;
    }

    public void setDeclarationId(long declarationId) {
        this.declarationId = declarationId;
    }

    public String getStatusFrom() {
        return statusFrom;
    }

    public void setStatusFrom(String statusFrom) {
        this.statusFrom = statusFrom;
    }

    public String getStatusTo() {
        return statusTo;
    }

    public void setStatusTo(String statusTo) {
        this.statusTo = statusTo;
    }

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
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
}
