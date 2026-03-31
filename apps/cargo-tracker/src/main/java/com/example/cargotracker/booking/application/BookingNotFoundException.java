package com.example.cargotracker.booking.application;

public class BookingNotFoundException extends RuntimeException {

    public BookingNotFoundException(String bookingId) {
        super("予約が見つかりません: " + bookingId);
    }
}
