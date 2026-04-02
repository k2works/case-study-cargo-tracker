package com.example.cargotracker.handling.application.internal.commandservices;

import java.util.UUID;

/**
 * 同一予約に RECEIVE イベントが重複して登録されようとした場合にスローされる例外。
 */
public class DuplicateReceiveException extends RuntimeException {

    public DuplicateReceiveException(UUID bookingId) {
        super("予約 " + bookingId + " には既に引取イベントが記録されています");
    }
}
