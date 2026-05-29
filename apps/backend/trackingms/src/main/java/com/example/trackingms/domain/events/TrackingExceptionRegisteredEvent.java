package com.example.trackingms.domain.events;

import com.example.trackingms.domain.model.ExceptionType;

import java.time.LocalDateTime;

/**
 * 追跡例外登録イベント（US19 / US20）。
 *
 * @param trackingNumber   追跡番号
 * @param exceptionId      例外識別子
 * @param type             例外種別
 * @param occurredAt       発生日時
 * @param occurredUnlocode 発生場所
 * @param description      発生状況・理由
 * @param escalated        管理職 escalation が必要か（LOSS のとき true）
 */
public record TrackingExceptionRegisteredEvent(
        String trackingNumber,
        String exceptionId,
        ExceptionType type,
        LocalDateTime occurredAt,
        String occurredUnlocode,
        String description,
        boolean escalated
) {
}
