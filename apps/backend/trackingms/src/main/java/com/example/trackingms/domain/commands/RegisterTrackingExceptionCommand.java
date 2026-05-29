package com.example.trackingms.domain.commands;

import com.example.trackingms.domain.model.ExceptionType;
import org.axonframework.modelling.command.TargetAggregateIdentifier;

import java.time.LocalDateTime;

/**
 * 追跡例外登録コマンド（US19 / US20）。
 *
 * <p>exceptionId は集約側で {@link com.example.trackingms.domain.model.TrackingExceptionId#generate()}
 * で自動採番する場合と、外部から指定する場合の両方に対応する。本コマンドでは外部から指定する設計
 * （Controller 側で UUID を生成して渡す）。</p>
 *
 * @param trackingNumber 集約識別子（追跡番号）
 * @param exceptionId    例外識別子（UUID 文字列、集約スコープで一意）
 * @param type           例外種別
 * @param occurredAt     発生日時
 * @param occurredUnlocode 発生場所（UN/LOCODE、任意）
 * @param description    発生状況・理由
 */
public record RegisterTrackingExceptionCommand(
        @TargetAggregateIdentifier String trackingNumber,
        String exceptionId,
        ExceptionType type,
        LocalDateTime occurredAt,
        String occurredUnlocode,
        String description
) {
}
