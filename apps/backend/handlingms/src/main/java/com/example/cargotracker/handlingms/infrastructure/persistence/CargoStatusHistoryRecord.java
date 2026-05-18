package com.example.cargotracker.handlingms.infrastructure.persistence;

import java.time.LocalDateTime;

/**
 * cargo_status_history テーブルの Read Model レコード（US17 暫定）。
 *
 * <p>IT6 で trackingms 新設時に {@code tracking_event} テーブルへ移行予定（ADR-0012）。</p>
 */
public class CargoStatusHistoryRecord {

    private String historyId;
    private String trackingNumber;
    private String newStatus;
    private String unlocode;
    private LocalDateTime updatedAt;
    private String operatorId;
    private LocalDateTime recordedAt;

    public String getHistoryId() {
        return historyId;
    }

    public void setHistoryId(String historyId) {
        this.historyId = historyId;
    }

    public String getTrackingNumber() {
        return trackingNumber;
    }

    public void setTrackingNumber(String trackingNumber) {
        this.trackingNumber = trackingNumber;
    }

    public String getNewStatus() {
        return newStatus;
    }

    public void setNewStatus(String newStatus) {
        this.newStatus = newStatus;
    }

    public String getUnlocode() {
        return unlocode;
    }

    public void setUnlocode(String unlocode) {
        this.unlocode = unlocode;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }

    public String getOperatorId() {
        return operatorId;
    }

    public void setOperatorId(String operatorId) {
        this.operatorId = operatorId;
    }

    public LocalDateTime getRecordedAt() {
        return recordedAt;
    }

    public void setRecordedAt(LocalDateTime recordedAt) {
        this.recordedAt = recordedAt;
    }
}
