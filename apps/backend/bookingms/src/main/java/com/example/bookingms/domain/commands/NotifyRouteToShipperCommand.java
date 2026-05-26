package com.example.bookingms.domain.commands;

import org.axonframework.modelling.command.TargetAggregateIdentifier;

/**
 * 確定経路の荷主通知コマンド（US12）。
 *
 * <p>経路提案中（ROUTE_PROPOSED）の予約について、確定経路の詳細を荷主に通知する。
 * 営業担当者が送信し、通知送信記録を Read Model に残す。</p>
 */
public record NotifyRouteToShipperCommand(
        @TargetAggregateIdentifier String bookingId
) {
}
