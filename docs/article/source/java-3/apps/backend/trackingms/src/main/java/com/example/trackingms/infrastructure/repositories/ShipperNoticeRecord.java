package com.example.trackingms.infrastructure.repositories;

import java.time.Instant;

/** {@code tracking_notice} の 1 行（番号つき）。 */
public class ShipperNoticeRecord {

    private long id;
    private String trackingNumber;
    private String message;
    private Instant noticedAt;

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

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public Instant getNoticedAt() {
        return noticedAt;
    }

    public void setNoticedAt(Instant noticedAt) {
        this.noticedAt = noticedAt;
    }
}
