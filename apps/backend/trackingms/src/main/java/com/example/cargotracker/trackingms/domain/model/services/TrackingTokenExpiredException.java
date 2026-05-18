package com.example.cargotracker.trackingms.domain.model.services;

/**
 * 追跡照会トークンの有効期限切れを表す例外（ADR-0013）。
 *
 * <p>API 層では HTTP 403 + {@code errorCode = "TOKEN_EXPIRED"} に変換される。
 * exp 経過、または delivered_at + grace 経過の両方を含む。</p>
 */
public class TrackingTokenExpiredException extends RuntimeException {

    public TrackingTokenExpiredException(String message) {
        super(message);
    }
}
