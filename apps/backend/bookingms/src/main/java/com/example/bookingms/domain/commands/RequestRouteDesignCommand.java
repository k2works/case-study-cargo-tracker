package com.example.bookingms.domain.commands;

import org.axonframework.modelling.command.TargetAggregateIdentifier;

/**
 * 経路設計引き渡しコマンド（US06）。
 *
 * <p>仮受付（PRELIMINARY）の予約を経路設計者に引き渡し、経路設計中（ROUTING）へ遷移させる。</p>
 */
public record RequestRouteDesignCommand(
        @TargetAggregateIdentifier String bookingId
) {
}
