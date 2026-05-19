package com.example.cargotracker.trackingms.interfaces.rest.dto;

import java.time.LocalDateTime;

/**
 * US19 追跡例外登録リクエスト DTO。
 *
 * @param exceptionType    例外種別（DELAY / DAMAGE / LOSS）
 * @param occurredAt       発生日時（省略時は現在日時）
 * @param occurredUnlocode 発生場所 UN/LOCODE（省略可）
 * @param description      説明
 * @param operatorId       オペレータ ID（省略時は "system"）
 */
public record RegisterTrackingExceptionRequest(
        String exceptionType,
        LocalDateTime occurredAt,
        String occurredUnlocode,
        String description,
        String operatorId) { }
