package com.example.trackingms.domain.events;

/**
 * 追跡番号発行済みドメインイベント
 *
 * <p>trackingms が追跡番号を発行したことを通知する。
 * bookingms がこのイベントを購読し、予約状態を TRACKING_ISSUED に遷移させる。
 */
public record TrackingNumberIssuedEvent(
        String bookingId,
        String trackingNumber
) {}
