package com.example.trackingms.domain.exceptions;

/**
 * 追跡アクティビティが見つからない場合にスローされる例外
 */
public class TrackingActivityNotFoundException extends RuntimeException {
    public TrackingActivityNotFoundException(String trackingNumber) {
        super("Tracking activity not found: " + trackingNumber);
    }
}
