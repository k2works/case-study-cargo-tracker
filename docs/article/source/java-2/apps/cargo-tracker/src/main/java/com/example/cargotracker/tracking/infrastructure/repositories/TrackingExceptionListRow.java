package com.example.cargotracker.tracking.infrastructure.repositories;

import java.time.Instant;

/** 例外一覧の行（追跡番号・予約 ID を含む）。 */
public class TrackingExceptionListRow {

    private long id;
    private String trackingNumber;
    private String bookingId;
    private String exceptionType;
    private String locationUnlocode;
    private Instant occurredAt;
    private String description;
    private boolean escalationFlag;
    private String statusBefore;
    private Instant resolvedAt;
    private String resolutionNotes;
    private java.time.LocalDate revisedArrival;

    public long getId() {
        return id;
    }

    public void setId(long id) {
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

    public String getExceptionType() {
        return exceptionType;
    }

    public void setExceptionType(String exceptionType) {
        this.exceptionType = exceptionType;
    }

    public String getLocationUnlocode() {
        return locationUnlocode;
    }

    public void setLocationUnlocode(String locationUnlocode) {
        this.locationUnlocode = locationUnlocode;
    }

    public Instant getOccurredAt() {
        return occurredAt;
    }

    public void setOccurredAt(Instant occurredAt) {
        this.occurredAt = occurredAt;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public boolean isEscalationFlag() {
        return escalationFlag;
    }

    public void setEscalationFlag(boolean escalationFlag) {
        this.escalationFlag = escalationFlag;
    }

    public String getStatusBefore() {
        return statusBefore;
    }

    public void setStatusBefore(String statusBefore) {
        this.statusBefore = statusBefore;
    }

    public Instant getResolvedAt() {
        return resolvedAt;
    }

    public void setResolvedAt(Instant resolvedAt) {
        this.resolvedAt = resolvedAt;
    }

    public String getResolutionNotes() {
        return resolutionNotes;
    }

    public void setResolutionNotes(String resolutionNotes) {
        this.resolutionNotes = resolutionNotes;
    }

    public java.time.LocalDate getRevisedArrival() {
        return revisedArrival;
    }

    public void setRevisedArrival(java.time.LocalDate revisedArrival) {
        this.revisedArrival = revisedArrival;
    }
}
