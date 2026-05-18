package com.example.cargotracker.trackingms.domain.model.services;

/**
 * 追跡照会トークンの署名不正・改ざん・形式不正を表す例外（ADR-0013）。
 *
 * <p>API 層では HTTP 401 + {@code errorCode = "TOKEN_INVALID"} に変換される。</p>
 */
public class InvalidTrackingTokenException extends RuntimeException {

    public InvalidTrackingTokenException(String message) {
        super(message);
    }

    public InvalidTrackingTokenException(String message, Throwable cause) {
        super(message, cause);
    }
}
