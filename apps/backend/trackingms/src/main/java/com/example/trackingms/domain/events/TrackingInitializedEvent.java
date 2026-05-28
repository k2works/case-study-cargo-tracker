package com.example.trackingms.domain.events;

/**
 * 追跡初期化イベント（US14）。
 *
 * <p>{@code TrackingActivity} 集約が初期化されたときに発行する。
 * 初期状態は {@code TransportStatus.NOT_RECEIVED}（domain-model.md / 受入条件）。</p>
 *
 * <p>本イベントは {@code tracking_summary} 投影の挿入トリガーとなり、また bookingms に
 * shared モジュールの {@code com.example.shared.events.CargoTrackedEvent} を通じて
 * 採番結果が反映される（{@code BookingSagaManager} の @SagaEventHandler で予約状態を
 * TRACKING_ISSUED に更新し @EndSaga）。</p>
 *
 * @param trackingNumber 採番済み追跡番号（{@code TrackingNumber} の値）
 * @param bookingId      予約識別子（Saga association）
 */
public record TrackingInitializedEvent(
        String trackingNumber,
        String bookingId
) {
}
