package com.example.bookingms.domain.commands;

import org.axonframework.modelling.command.TargetAggregateIdentifier;

/**
 * 予約キャンセルコマンド（US13）。
 *
 * <p>確定済み・キャンセル済み以外の予約をキャンセル（CANCELLED）する。</p>
 */
public record CancelBookingCommand(
        @TargetAggregateIdentifier String bookingId
) {
}
