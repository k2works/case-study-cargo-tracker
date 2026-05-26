package com.example.shared.events;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 経路確定イベント（US11、cross-service）。
 *
 * <p>routingms が経路設計者の確定操作で発行し、bookingms が Kafka 経由で購読する（ADR-0009）。
 * IT3 の {@link RouteDesignRequestedEvent}（bookingms → routingms）と対になる
 * routingms → bookingms の逆方向 cross-service イベント。</p>
 *
 * <p>cross-service の安定契約として shared モジュールに配置し、routingms / bookingms が同一 FQCN で
 * シリアライズ・デシリアライズできるようにする。bookingms が予約に経路を紐付けられるよう、確定経路の
 * 輸送区間（{@link LegData}）の列を自己完結で保持する。</p>
 */
public record RouteConfirmedEvent(
        String bookingId,
        List<LegData> legs
) {
    /**
     * 確定経路を構成する輸送区間。航海番号・積込港・荷降し港と日時を保持する。
     */
    public record LegData(
            String voyageNumber,
            String loadUnlocode,
            String unloadUnlocode,
            LocalDateTime loadTime,
            LocalDateTime unloadTime
    ) {
    }
}
