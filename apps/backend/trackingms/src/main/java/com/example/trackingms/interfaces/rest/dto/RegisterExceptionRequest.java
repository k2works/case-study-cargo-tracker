package com.example.trackingms.interfaces.rest.dto;

import com.example.trackingms.domain.model.ExceptionType;

import java.time.LocalDateTime;

/**
 * 追跡例外登録リクエスト（US19 / US20）。
 *
 * @param type             例外種別（DELAY / DAMAGE / LOSS）
 * @param occurredAt       発生日時
 * @param occurredUnlocode 発生場所（UN/LOCODE、任意）
 * @param description      発生状況・理由（必須）
 */
public record RegisterExceptionRequest(
        ExceptionType type,
        LocalDateTime occurredAt,
        String occurredUnlocode,
        String description
) {
}
