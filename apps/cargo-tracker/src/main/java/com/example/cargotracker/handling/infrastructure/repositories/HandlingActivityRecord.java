package com.example.cargotracker.handling.infrastructure.repositories;

import java.time.Instant;
import java.util.UUID;

/** 荷役作業記録の行。 */
public class HandlingActivityRecord {

    private Long id;
    private UUID bookingId;
    private String eventType;
    private Instant eventCompletionTime;
    private String locationUnlocode;
    private String voyageNumber;
    private String trackingNumber;
    private String operatorName;
    private long version;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public UUID getBookingId() {
        return bookingId;
    }

    public void setBookingId(UUID bookingId) {
        this.bookingId = bookingId;
    }

    public String getEventType() {
        return eventType;
    }

    public void setEventType(String eventType) {
        this.eventType = eventType;
    }

    public Instant getEventCompletionTime() {
        return eventCompletionTime;
    }

    public void setEventCompletionTime(Instant eventCompletionTime) {
        this.eventCompletionTime = eventCompletionTime;
    }

    public String getLocationUnlocode() {
        return locationUnlocode;
    }

    public void setLocationUnlocode(String locationUnlocode) {
        this.locationUnlocode = locationUnlocode;
    }

    public String getVoyageNumber() {
        return voyageNumber;
    }

    public void setVoyageNumber(String voyageNumber) {
        this.voyageNumber = voyageNumber;
    }

    /** 読み取った追跡番号（V13）。 */
    public String getTrackingNumber() {
        return trackingNumber;
    }

    public void setTrackingNumber(String trackingNumber) {
        this.trackingNumber = trackingNumber;
    }

    public String getOperatorName() {
        return operatorName;
    }

    public void setOperatorName(String operatorName) {
        this.operatorName = operatorName;
    }

    public long getVersion() {
        return version;
    }

    public void setVersion(long version) {
        this.version = version;
    }
}
