package com.example.cargotracker.handling.application.internal.commandservices;

/**
 * 予約が見つからない場合にスローされる例外。
 */
public class BookingNotFoundException extends RuntimeException {

    public BookingNotFoundException(String bookingId) {
        super("予約が見つかりません: " + bookingId);
    }
}
