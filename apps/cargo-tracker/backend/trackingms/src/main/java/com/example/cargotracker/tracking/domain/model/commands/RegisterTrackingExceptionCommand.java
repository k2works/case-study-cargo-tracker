package com.example.cargotracker.tracking.domain.model.commands;

import com.example.cargotracker.tracking.domain.model.valueobjects.ExceptionType;
import java.time.Instant;
import org.axonframework.modelling.annotation.TargetEntityId;

/**
 * 輸送中の例外を起票する（UC16 / US19 §受入基準 1）。
 *
 * <p>送るのは追跡管理者（S43）と、<b>自動起票</b>（予定外の荷役は `MISROUTE`、
 * 通関の留置は `CUSTOMS_HOLD`）。</p>
 *
 * <p><b>緊急かどうかは載せない。</b> {@code ExceptionType#urgent} が答える
 * （不変条件 7）——載せると、起票した人が「急ぎではない紛失」を作れてしまう。</p>
 */
public record RegisterTrackingExceptionCommand(
        @TargetEntityId String trackingNumber,
        String exceptionId,
        ExceptionType type,
        Instant occurredAt,
        String unLocode,
        String description,
        String reportedBy) {
}
