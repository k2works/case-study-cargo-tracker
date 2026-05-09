package com.example.bookingms.domain.events;

/**
 * 追跡番号発行済みドメインイベント（受信用）
 *
 * <p>trackingms から送信され、bookingms が購読する。
 * 予約状態を TRACKING_ISSUED に遷移させるトリガーとなる。
 */
public record TrackingNumberIssuedEvent(
        String bookingId,
        String trackingNumber
) {}
