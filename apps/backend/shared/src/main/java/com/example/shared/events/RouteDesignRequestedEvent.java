package com.example.shared.events;

import java.time.LocalDate;

/**
 * 経路設計依頼イベント（US06、cross-service）。
 *
 * <p>bookingms が予約状態を ROUTING に遷移したとき発行し、routingms が Kafka 経由で購読する（ADR-0009）。
 * cross-service の安定契約として shared モジュールに配置し、bookingms / routingms が同一 FQCN で
 * シリアライズ・デシリアライズできるようにする。routingms が経路設計に着手できるよう、出発地・目的地・
 * 到着期限・貨物種別を自己完結で保持する（cross-service イベントは受信側が処理に必要な情報を含む）。
 * bookingms 内では cargo_summary の booking_status 更新トリガーにもなる。</p>
 */
public record RouteDesignRequestedEvent(
        String bookingId,
        String bookingStatus,
        String originUnlocode,
        String destinationUnlocode,
        LocalDate arrivalDeadline,
        String cargoType
) {
}
