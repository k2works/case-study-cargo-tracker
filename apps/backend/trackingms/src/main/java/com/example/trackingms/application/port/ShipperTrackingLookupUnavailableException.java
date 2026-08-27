package com.example.trackingms.application.port;

/** 荷主境界の確認先に到達できない、または配線が誤っている。 */
public class ShipperTrackingLookupUnavailableException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public ShipperTrackingLookupUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
