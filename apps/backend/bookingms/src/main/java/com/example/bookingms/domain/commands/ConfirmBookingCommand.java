package com.example.bookingms.domain.commands;

import org.axonframework.modelling.command.TargetAggregateIdentifier;

/**
 * 予約確定コマンド（US13）。
 *
 * <p>経路提案中（ROUTE_PROPOSED）の予約を確定（CONFIRMED）する。</p>
 */
public record ConfirmBookingCommand(
        @TargetAggregateIdentifier String bookingId
) {
}
