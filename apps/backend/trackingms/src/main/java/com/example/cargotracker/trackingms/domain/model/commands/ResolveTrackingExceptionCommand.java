package com.example.cargotracker.trackingms.domain.model.commands;

import org.axonframework.modelling.annotation.TargetEntityId;

/**
 * 追跡例外を解決するコマンド（US20）。
 *
 * @param trackingNumber 追跡番号（Aggregate ID）
 * @param exceptionId    解決対象例外 ID
 * @param resolution     解決内容
 * @param operatorId     解決オペレータ ID
 */
public record ResolveTrackingExceptionCommand(
        @TargetEntityId String trackingNumber,
        String exceptionId,
        String resolution,
        String operatorId) { }
