package com.example.cargotracker.exception.application.internal.commandservices;

/**
 * 追跡番号が見つからない場合にスローする例外。
 */
public class TrackingNotFoundException extends RuntimeException {
    public TrackingNotFoundException(String trackingNumber) {
        super("追跡番号が見つかりません: " + trackingNumber);
    }
}
