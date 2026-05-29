package com.example.trackingms.domain.events;

import com.example.trackingms.domain.model.ExceptionType;

import java.time.LocalDateTime;

/**
 * 追跡例外 escalation イベント（US20 受入基準 3）。
 *
 * <p>{@code ExceptionType = LOSS} のときに {@code TrackingExceptionRegisteredEvent} と同時に発行され、
 * {@code TrackingNotificationEventHandler} が {@code NotificationAcl.notifyExceptionEscalation} を呼び出して
 * 管理職通知のスタブ実行（実メール送信は IT8 で別途）を行う。</p>
 *
 * @param trackingNumber 追跡番号
 * @param exceptionId    例外識別子
 * @param type           例外種別（実用上は LOSS のみ）
 * @param occurredAt     発生日時
 */
public record TrackingExceptionEscalatedEvent(
        String trackingNumber,
        String exceptionId,
        ExceptionType type,
        LocalDateTime occurredAt
) {
}
