package com.example.cargotracker.billing.application.internal.commandservices;

/**
 * 確定済み予約が見つからない場合にスローされる例外。
 */
public class BookingNotFoundException extends RuntimeException {

    public BookingNotFoundException(String bookingId) {
        super("確定済み予約が見つかりません: " + bookingId);
    }
}
