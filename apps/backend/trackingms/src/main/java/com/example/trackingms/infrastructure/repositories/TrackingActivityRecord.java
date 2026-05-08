package com.example.trackingms.infrastructure.repositories;

/**
 * tracking_activity テーブルの MyBatis レコード
 */
public class TrackingActivityRecord {
    private Long id;
    private String trackingNumber;
    private String bookingId;
    private String transportStatus;

    public TrackingActivityRecord() {}

    public TrackingActivityRecord(String trackingNumber, String bookingId, String transportStatus) {
        this.trackingNumber = trackingNumber;
        this.bookingId = bookingId;
        this.transportStatus = transportStatus;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
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

    public String getTransportStatus() {
        return transportStatus;
    }

    public void setTransportStatus(String transportStatus) {
        this.transportStatus = transportStatus;
    }
}
