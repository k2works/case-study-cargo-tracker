package com.example.trackingms.infrastructure.persistence;

import java.time.Instant;

/** {@code tracking_notice} の 1 行。 */
public class TrackingNoticeRecord {

    private String message;
    private Instant noticedAt;

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
