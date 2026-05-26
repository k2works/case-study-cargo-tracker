package com.example.bookingms.domain.commands;

import com.example.bookingms.domain.model.Leg;
import org.axonframework.modelling.command.TargetAggregateIdentifier;

import java.util.List;

/**
 * 経路割当コマンド（US11）。
 *
 * <p>routingms の経路確定（{@code RouteConfirmedEvent}）を受けた cross-service ハンドラが Saga 経由で発行する。
 * 経路設計中（ROUTING）の予約に確定旅程（{@link Leg} の列）を割り当て、経路提案中（ROUTE_PROPOSED）へ遷移させる。</p>
 */
public record AssignRouteToCargoCommand(
        @TargetAggregateIdentifier String bookingId,
        List<Leg> legs
) {
}
