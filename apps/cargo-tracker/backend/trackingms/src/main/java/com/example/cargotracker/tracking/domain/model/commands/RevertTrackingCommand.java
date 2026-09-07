package com.example.cargotracker.tracking.domain.model.commands;

import java.time.Instant;
import org.axonframework.modelling.annotation.TargetEntityId;

/**
 * 取り消された荷役の分だけ貨物状態を戻す（UC13 / 不変条件 11）。
 *
 * <p><b>取り消しの事実はイベントとして残る。</b> 状態は戻るが、履歴からは
 * 「進めて、戻した」ことが読める。</p>
 */
public record RevertTrackingCommand(
        @TargetEntityId String trackingNumber,
        String handlingType,
        String reason,
        String revertedBy,
        Instant revertedAt) {
}
