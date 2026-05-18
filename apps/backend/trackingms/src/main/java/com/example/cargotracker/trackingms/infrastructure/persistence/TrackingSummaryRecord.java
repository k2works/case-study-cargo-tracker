package com.example.cargotracker.trackingms.infrastructure.persistence;

import java.time.LocalDateTime;

/**
 * tracking_summary テーブルの Read Model レコード（MyBatis 用 POJO）。
 *
 * <p>変数名規約: 本クラスの参照変数は {@code summary} を使用する（IT5 ふりかえり T3）。</p>
 *
 * <p>本来は {@code record} が理想だが、MyBatis のデフォルトリフレクションは
 * 自動的に record の {@code <init>} を解決できないため、{@code <constructor>} 要素を
 * 各 XML で書くか POJO とする必要がある。handlingms と一貫させて POJO を採用する。</p>
 */
public class TrackingSummaryRecord {

    private String trackingNumber;
    private String bookingId;
    private String currentStatus;
    private String currentUnlocode;
    private String currentVoyageNumber;
    private String originUnlocode;
    private String destinationUnlocode;
    private LocalDateTime estimatedArrival;
    private LocalDateTime deliveredAt;
    private boolean misrouted;
    private LocalDateTime lastEventAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private long version;

    public TrackingSummaryRecord() {
        // MyBatis default constructor
    }

    // S107: Read Model POJO は tracking_summary テーブルの全列を表現するため引数 7 個制限を緩和する
    @SuppressWarnings("java:S107")
    public TrackingSummaryRecord(
            String trackingNumber,
            String bookingId,
            String currentStatus,
            String currentUnlocode,
            String currentVoyageNumber,
            String originUnlocode,
            String destinationUnlocode,
            LocalDateTime estimatedArrival,
            LocalDateTime deliveredAt,
            boolean misrouted,
            LocalDateTime lastEventAt,
            LocalDateTime createdAt,
            LocalDateTime updatedAt,
            long version) {
        this.trackingNumber = trackingNumber;
        this.bookingId = bookingId;
        this.currentStatus = currentStatus;
        this.currentUnlocode = currentUnlocode;
        this.currentVoyageNumber = currentVoyageNumber;
        this.originUnlocode = originUnlocode;
        this.destinationUnlocode = destinationUnlocode;
        this.estimatedArrival = estimatedArrival;
        this.deliveredAt = deliveredAt;
        this.misrouted = misrouted;
        this.lastEventAt = lastEventAt;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
        this.version = version;
    }

    public String trackingNumber() {
        return trackingNumber;
    }

    public String bookingId() {
        return bookingId;
    }

    public String currentStatus() {
        return currentStatus;
    }

    public String currentUnlocode() {
        return currentUnlocode;
    }

    public String currentVoyageNumber() {
        return currentVoyageNumber;
    }

    public String originUnlocode() {
        return originUnlocode;
    }

    public String destinationUnlocode() {
        return destinationUnlocode;
    }

    public LocalDateTime estimatedArrival() {
        return estimatedArrival;
    }

    public LocalDateTime deliveredAt() {
        return deliveredAt;
    }

    public boolean misrouted() {
        return misrouted;
    }

    public LocalDateTime lastEventAt() {
        return lastEventAt;
    }

    public LocalDateTime createdAt() {
        return createdAt;
    }

    public LocalDateTime updatedAt() {
        return updatedAt;
    }

    public long version() {
        return version;
    }

    public String getTrackingNumber() {
        return trackingNumber;
    }

    public void setTrackingNumber(String trackingNumber) {
        this.trackingNumber = trackingNumber;
    }

    public String getBookingId() {
        return bookingId;
    }

    public void setBookingId(String bookingId) {
        this.bookingId = bookingId;
    }

    public String getCurrentStatus() {
        return currentStatus;
    }

    public void setCurrentStatus(String currentStatus) {
        this.currentStatus = currentStatus;
    }

    public String getCurrentUnlocode() {
        return currentUnlocode;
    }

    public void setCurrentUnlocode(String currentUnlocode) {
        this.currentUnlocode = currentUnlocode;
    }

    public String getCurrentVoyageNumber() {
        return currentVoyageNumber;
    }

    public void setCurrentVoyageNumber(String currentVoyageNumber) {
        this.currentVoyageNumber = currentVoyageNumber;
    }

    public String getOriginUnlocode() {
        return originUnlocode;
    }

    public void setOriginUnlocode(String originUnlocode) {
        this.originUnlocode = originUnlocode;
    }

    public String getDestinationUnlocode() {
        return destinationUnlocode;
    }

    public void setDestinationUnlocode(String destinationUnlocode) {
        this.destinationUnlocode = destinationUnlocode;
    }

    public LocalDateTime getEstimatedArrival() {
        return estimatedArrival;
    }

    public void setEstimatedArrival(LocalDateTime estimatedArrival) {
        this.estimatedArrival = estimatedArrival;
    }

    public LocalDateTime getDeliveredAt() {
        return deliveredAt;
    }

    public void setDeliveredAt(LocalDateTime deliveredAt) {
        this.deliveredAt = deliveredAt;
    }

    public boolean isMisrouted() {
        return misrouted;
    }

    public void setMisrouted(boolean misrouted) {
        this.misrouted = misrouted;
    }

    public LocalDateTime getLastEventAt() {
        return lastEventAt;
    }

    public void setLastEventAt(LocalDateTime lastEventAt) {
        this.lastEventAt = lastEventAt;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }

    public long getVersion() {
        return version;
    }

    public void setVersion(long version) {
        this.version = version;
    }
}
