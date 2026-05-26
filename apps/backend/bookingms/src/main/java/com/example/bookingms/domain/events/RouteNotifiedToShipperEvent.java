package com.example.bookingms.domain.events;

/**
 * 確定経路の荷主通知イベント（US12）。
 *
 * <p>{@code NotifyRouteToShipperCommand} の処理で発行され、通知送信記録（cargo_summary.route_notified_at）の
 * 更新トリガーとなる。予約状態（ROUTE_PROPOSED）は変更しない。通知日時は Read Model 側で記録する。</p>
 */
public record RouteNotifiedToShipperEvent(
        String bookingId
) {
}
