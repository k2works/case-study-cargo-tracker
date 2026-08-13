package com.example.cargotracker.tracking.infrastructure.repositories;

import java.util.UUID;

/** 追跡レコードの行。 */
public class TrackingActivityRecord {

    private Long id;
    private String trackingNumber;
    private UUID bookingId;
    private String transportStatus;
    private long version;
    private String destinationUnlocode;
    private java.time.LocalDate estimatedArrivalDate;

    public String getDestinationUnlocode() {
        return destinationUnlocode;
    }

    public void setDestinationUnlocode(String destinationUnlocode) {
        this.destinationUnlocode = destinationUnlocode;
    }

    public java.time.LocalDate getEstimatedArrivalDate() {
        return estimatedArrivalDate;
    }

    public void setEstimatedArrivalDate(java.time.LocalDate estimatedArrivalDate) {
        this.estimatedArrivalDate = estimatedArrivalDate;
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

    public UUID getBookingId() {
        return bookingId;
    }

    public void setBookingId(UUID bookingId) {
        this.bookingId = bookingId;
    }

    public String getTransportStatus() {
        return transportStatus;
    }

    public void setTransportStatus(String transportStatus) {
        this.transportStatus = transportStatus;
    }

    public long getVersion() {
        return version;
    }

    public void setVersion(long version) {
        this.version = version;
    }

    /** 引取の直前の輸送状態（US36）。引取前・列が無かったころは {@code null}。 */
    private String statusBeforeClaim;

    public String getStatusBeforeClaim() {
        return statusBeforeClaim;
    }

    public void setStatusBeforeClaim(String statusBeforeClaim) {
        this.statusBeforeClaim = statusBeforeClaim;
    }
}
