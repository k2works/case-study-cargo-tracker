package com.example.trackingms.domain.events;

import java.time.LocalDateTime;

/**
 * 追跡例外解決イベント（US19 / US20）。
 *
 * @param trackingNumber 追跡番号
 * @param exceptionId    例外識別子
 * @param resolution     対応内容
 * @param resolvedAt     解決日時
 */
public record TrackingExceptionResolvedEvent(
        String trackingNumber,
        String exceptionId,
        String resolution,
        LocalDateTime resolvedAt
) {
}
