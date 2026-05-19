package com.example.cargotracker.trackingms.domain.model.commands;

import java.time.LocalDateTime;
import org.axonframework.modelling.annotation.TargetEntityId;

/**
 * 追跡例外を登録するコマンド（US19）。
 *
 * @param trackingNumber 追跡番号（Aggregate ID）
 * @param exceptionId    例外 ID（UUID、呼び出し元が生成）
 * @param exceptionType  例外種別（DELAY / DAMAGE / LOSS）
 * @param occurredAt     発生日時
 * @param occurredUnlocode 発生場所 UN/LOCODE（省略可）
 * @param description    説明
 * @param operatorId     登録オペレータ ID
 */
public record RegisterTrackingExceptionCommand(
        @TargetEntityId String trackingNumber,
        String exceptionId,
        String exceptionType,
        LocalDateTime occurredAt,
        String occurredUnlocode,
        String description,
        String operatorId) { }
