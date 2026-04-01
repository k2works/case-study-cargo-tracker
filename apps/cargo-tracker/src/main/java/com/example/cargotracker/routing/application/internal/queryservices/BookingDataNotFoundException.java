package com.example.cargotracker.routing.application.internal.queryservices;

import java.util.UUID;

/**
 * ルート検索に必要な予約データが見つからない場合にスローされる例外。
 *
 * <p>routing コンテキスト固有の例外。booking コンテキストの同名例外と区別するために
 * "ForRouting" サフィックスを省略しつつ "BookingData" で一意性を保つ。
 */
public class BookingDataNotFoundException extends RuntimeException {

    private final UUID bookingId;

    public BookingDataNotFoundException(UUID bookingId) {
        super("ルート検索に必要な予約データが見つかりません: " + bookingId);
        this.bookingId = bookingId;
    }

    public UUID getBookingId() {
        return bookingId;
    }
}
