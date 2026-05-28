package com.example.shared.events;

/**
 * 追跡採番完了イベント（US14、cross-service）。
 *
 * <p>trackingms が {@link TrackingIssuanceRequestedEvent} を受けて {@code TrackingActivity} を NOT_RECEIVED で
 * 初期化し、追跡番号（{@code TRK-} + 大文字英数 10 桁）を採番した直後に発行する。bookingms の
 * {@code BookingSagaManager} が Kafka 経由で購読し、予約状態を TRACKING_ISSUED に更新して
 * {@code @EndSaga} で Saga を終了する（IT5 1.4、ADR-0009）。</p>
 *
 * <p>cross-service の安定契約として shared モジュールに配置し、trackingms / bookingms が同一 FQCN で
 * シリアライズ・デシリアライズできるようにする。Saga association（{@code bookingId}）と採番結果
 * （{@code trackingNumber}）の最小ペイロードに留め、後続の状態遷移は別イベント
 * （{@link HandlingActivityRegisteredEvent} 経由など）で伝える。</p>
 *
 * <p>受信側ハンドラは ADR-0011（ホワイトリスト方式）に従い、
 * {@code AggregateNotFoundException} / {@code CommandExecutionException} の 2 種のみ WARN スキップし、
 * それ以外の例外は伝播させる。</p>
 */
public record CargoTrackedEvent(
        String bookingId,
        String trackingNumber
) {
}
