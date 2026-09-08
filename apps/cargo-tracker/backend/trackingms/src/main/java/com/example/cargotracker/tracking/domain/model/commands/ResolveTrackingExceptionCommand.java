package com.example.cargotracker.tracking.domain.model.commands;

import org.axonframework.modelling.annotation.TargetEntityId;

/**
 * 例外を解決する（UC16 / US19 §受入基準 4）。
 *
 * <p><b>解決しても例外は消えない</b>（不変条件 6）。貨物状態は例外前へ戻るが
 * （不変条件 5）、<b>起票中の例外がすべて解決したときだけ</b>である。</p>
 */
public record ResolveTrackingExceptionCommand(
        @TargetEntityId String trackingNumber,
        String exceptionId,
        String resolution,
        String resolvedBy) {
}
