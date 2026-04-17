package com.example.cargotracker.tracking.infrastructure.repositories;

public class TrackingRecord {

    private Long id;
    private String trackingNumber;
    private String bookingId;
    private String cargoStatus;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getTrackingNumber() { return trackingNumber; }
    public void setTrackingNumber(String trackingNumber) { this.trackingNumber = trackingNumber; }

    public String getBookingId() { return bookingId; }
    public void setBookingId(String bookingId) { this.bookingId = bookingId; }

    public String getCargoStatus() { return cargoStatus; }
    public void setCargoStatus(String cargoStatus) { this.cargoStatus = cargoStatus; }
}
