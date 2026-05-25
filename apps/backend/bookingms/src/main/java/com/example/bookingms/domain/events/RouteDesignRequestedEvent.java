package com.example.bookingms.domain.events;

/**
 * 経路設計依頼イベント（US06）。
 *
 * <p>予約状態が ROUTING に遷移したことを表す。routingms へ cross-service 連携され（ADR-0009）、
 * cargo_summary の booking_status 更新トリガーにもなる。</p>
 */
public record RouteDesignRequestedEvent(
        String bookingId,
        String bookingStatus
) {
}
