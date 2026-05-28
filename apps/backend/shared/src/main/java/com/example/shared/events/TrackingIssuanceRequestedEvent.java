package com.example.shared.events;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 追跡発行依頼イベント（US14、cross-service）。
 *
 * <p>bookingms の {@code BookingSagaManager} が予約確定（{@code BookingConfirmedEvent}）を受けて発行し、
 * trackingms が Kafka 経由で購読する（ADR-0009）。trackingms は本イベントから {@code InitializeTrackingCommand}
 * を発行して {@code TrackingActivity} 集約を NOT_RECEIVED 初期化・採番し、結果（追跡番号）を
 * {@link CargoTrackedEvent} で bookingms に通知して Saga を終了する（IT5 1.2）。</p>
 *
 * <p>cross-service の安定契約として shared モジュールに配置し、bookingms / trackingms が同一 FQCN で
 * シリアライズ・デシリアライズできるようにする。trackingms が追跡を自己完結で初期化できるよう、追跡対象の
 * 識別子と確定旅程・到着期限・貨物種別を保持する（cross-service イベントは受信側が処理に必要な情報を含む）。</p>
 *
 * <p>受信側ハンドラは ADR-0011（ホワイトリスト方式）に従い、
 * {@code AggregateNotFoundException} / {@code CommandExecutionException} の 2 種のみ WARN スキップし、
 * それ以外の例外は伝播させる。</p>
 */
public record TrackingIssuanceRequestedEvent(
        String bookingId,
        String originUnlocode,
        String destinationUnlocode,
        LocalDate arrivalDeadline,
        String cargoType,
        List<LegData> itinerary
) {
    /**
     * 確定旅程を構成する輸送区間。航海番号・積込港・荷降し港と日時を保持する。
     * {@link RouteConfirmedEvent.LegData} と同形だが、cross-service イベントの契約独立性を確保するため
     * 共有せず本イベント固有の record として定義する。
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
