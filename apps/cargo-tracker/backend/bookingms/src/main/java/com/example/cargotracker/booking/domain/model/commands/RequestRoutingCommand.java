package com.example.cargotracker.booking.domain.model.commands;

import org.axonframework.modelling.annotation.TargetEntityId;

/**
 * 予約情報を経路設計者に引き渡す（UC04 / US06）。
 *
 * <p>状態遷移を持つ最初のコマンド。IT2 までは {@code [*] → PRELIMINARY} だけだった。</p>
 */
public record RequestRoutingCommand(
        @TargetEntityId String bookingId,
        String requestedBy) {
}
