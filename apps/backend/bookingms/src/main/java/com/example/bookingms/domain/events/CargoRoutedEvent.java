package com.example.bookingms.domain.events;

import com.example.bookingms.domain.model.Leg;

import java.util.List;

/**
 * 経路確定イベント（US11）。
 *
 * <p>{@code AssignRouteToCargoCommand} の処理で発行され、予約状態を経路提案中（ROUTE_PROPOSED）・
 * 経路設定状態を設定済み（ROUTED）へ更新する。Read Model（cargo_summary の状態更新・cargo_leg の確定）の
 * 更新トリガーとなり、BookingSagaManager の経路設計フェーズを進める。</p>
 */
public record CargoRoutedEvent(
        String bookingId,
        String bookingStatus,
        String routingStatus,
        List<Leg> legs
) {
}
