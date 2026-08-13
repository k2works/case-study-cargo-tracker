package com.example.cargotracker.handling.infrastructure.repositories;

import java.time.Instant;

/** 通関申告一覧の 1 行（読み取り）。 */
public class CustomsListRow {

    private long id;
    private String declarationNumber;
    private String trackingNumber;
    private String bookingId;
    private String status;
    private Instant declaredAt;
    private Instant clearedAt;
    private Instant heldSince;
    private String shipperName;

    public long getId() {
        return id;
    }

    public void setId(long id) {
        this.id = id;
    }

    public String getDeclarationNumber() {
        return declarationNumber;
    }

    public void setDeclarationNumber(String declarationNumber) {
        this.declarationNumber = declarationNumber;
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

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public Instant getDeclaredAt() {
        return declaredAt;
    }

    public void setDeclaredAt(Instant declaredAt) {
        this.declaredAt = declaredAt;
    }

    public Instant getClearedAt() {
        return clearedAt;
    }

    public void setClearedAt(Instant clearedAt) {
        this.clearedAt = clearedAt;
    }

    public Instant getHeldSince() {
        return heldSince;
    }

    public void setHeldSince(Instant heldSince) {
        this.heldSince = heldSince;
    }

    public String getShipperName() {
        return shipperName;
    }

    public void setShipperName(String shipperName) {
        this.shipperName = shipperName;
    }
}
