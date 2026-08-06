package com.example.cargotracker.tracking.infrastructure.repositories;

import java.time.LocalDateTime;

public class HandlingEventWithBookingRecord {

    private Long id;
    private String trackingNumber;
    private String bookingId;
    private String eventType;
    private String locationUnlocode;
    private LocalDateTime completionTime;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getTrackingNumber() { return trackingNumber; }
    public void setTrackingNumber(String trackingNumber) { this.trackingNumber = trackingNumber; }

    public String getBookingId() { return bookingId; }
    public void setBookingId(String bookingId) { this.bookingId = bookingId; }

    public String getEventType() { return eventType; }
    public void setEventType(String eventType) { this.eventType = eventType; }

    public String getLocationUnlocode() { return locationUnlocode; }
    public void setLocationUnlocode(String locationUnlocode) { this.locationUnlocode = locationUnlocode; }

    public LocalDateTime getCompletionTime() { return completionTime; }
    public void setCompletionTime(LocalDateTime completionTime) { this.completionTime = completionTime; }
}
