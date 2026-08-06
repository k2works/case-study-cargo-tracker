package com.example.cargotracker.billing.application.internal.outboundservices.acl;

public interface BookingSettlementPort {

    void settleBooking(String bookingId);
}
