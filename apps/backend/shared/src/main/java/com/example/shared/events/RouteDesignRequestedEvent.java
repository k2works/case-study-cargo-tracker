package com.example.shared.events;

/**
 * 経路設計依頼イベント（US06、cross-service）。
 *
 * <p>bookingms が予約状態を ROUTING に遷移したとき発行し、routingms が Kafka 経由で購読する（ADR-0009）。
 * cross-service の安定契約として shared モジュールに配置し、bookingms / routingms が同一 FQCN で
 * シリアライズ・デシリアライズできるようにする。bookingms 内では cargo_summary の booking_status
 * 更新トリガーにもなる。</p>
 */
public record RouteDesignRequestedEvent(
        String bookingId,
        String bookingStatus
) {
}
