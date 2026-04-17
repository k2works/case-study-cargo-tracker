package com.example.cargotracker.booking.application.internal.outboundservices.acl;

public interface TrackingPort {

    String issueTrackingNumber(String bookingId);
}
