package com.example.cargotracker.booking.domain.model.exceptions;

import com.example.cargotracker.booking.domain.model.valueobjects.BookingId;

public class BookingNotFoundException extends RuntimeException {

    private final BookingId bookingId;

    public BookingNotFoundException(BookingId bookingId) {
        super("Booking not found: " + bookingId);
        this.bookingId = bookingId;
    }

    public BookingId getBookingId() {
        return bookingId;
    }
}
