package com.example.trackingms.domain.projections;

import java.time.LocalDateTime;

/**
 * 追跡 Read Model (POJO + MyBatis ResultMap)。
 *
 * <p>tracking_summary テーブルの各カラムに対応するフィールド。
 * MyBatis が setter を呼び出して値を設定する（US14・US18）。</p>
 */
public class TrackingSummary {

    private String trackingNumber;
    private String bookingId;
    private String currentStatus;
    private String currentUnlocode;
    private String currentVoyageNumber;
    private LocalDateTime estimatedArrival;
    private boolean misrouted;
    private LocalDateTime lastEventAt;
    private LocalDateTime deliveredAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private Long version;

    public TrackingSummary() { /* MyBatis result mapping */ }

    public String getTrackingNumber() { return trackingNumber; }
    public void setTrackingNumber(String trackingNumber) { this.trackingNumber = trackingNumber; }

    public String getBookingId() { return bookingId; }
    public void setBookingId(String bookingId) { this.bookingId = bookingId; }

    public String getCurrentStatus() { return currentStatus; }
    public void setCurrentStatus(String currentStatus) { this.currentStatus = currentStatus; }

    public String getCurrentUnlocode() { return currentUnlocode; }
    public void setCurrentUnlocode(String currentUnlocode) { this.currentUnlocode = currentUnlocode; }

    public String getCurrentVoyageNumber() { return currentVoyageNumber; }
    public void setCurrentVoyageNumber(String currentVoyageNumber) { this.currentVoyageNumber = currentVoyageNumber; }

    public LocalDateTime getEstimatedArrival() { return estimatedArrival; }
    public void setEstimatedArrival(LocalDateTime estimatedArrival) { this.estimatedArrival = estimatedArrival; }

    public boolean isMisrouted() { return misrouted; }
    public void setMisrouted(boolean misrouted) { this.misrouted = misrouted; }

    public LocalDateTime getLastEventAt() { return lastEventAt; }
    public void setLastEventAt(LocalDateTime lastEventAt) { this.lastEventAt = lastEventAt; }

    public LocalDateTime getDeliveredAt() { return deliveredAt; }
    public void setDeliveredAt(LocalDateTime deliveredAt) { this.deliveredAt = deliveredAt; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }

    public Long getVersion() { return version; }
    public void setVersion(Long version) { this.version = version; }
}
