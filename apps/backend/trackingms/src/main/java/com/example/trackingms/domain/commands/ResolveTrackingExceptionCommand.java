package com.example.trackingms.domain.commands;

import org.axonframework.modelling.command.TargetAggregateIdentifier;

/**
 * 追跡例外を解決済みに遷移させるコマンド（US19 / US20 受入基準 4/5）。
 *
 * @param trackingNumber 集約識別子（追跡番号）
 * @param exceptionId    解決する例外識別子
 * @param resolution     対応内容（補償方針・代替ルート等）。RESOLVED 遷移時に必須
 */
public record ResolveTrackingExceptionCommand(
        @TargetAggregateIdentifier String trackingNumber,
        String exceptionId,
        String resolution
) {
}
