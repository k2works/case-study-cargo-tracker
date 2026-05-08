package com.example.bookingms.domain.events;

/**
 * 経路割当完了ドメインイベント
 *
 * <p>bookingms が経路を割り当て、予約状態が ROUTE_PROPOSED に遷移したことを通知する。
 * trackingms がこのイベントを購読し、追跡レコードを初期化する。
 */
public record CargoRoutedEvent(
        String bookingId,
        String originUnlocode,
        String destinationUnlocode
) {}
