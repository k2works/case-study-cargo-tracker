package com.example.trackingms.domain.events;

import com.example.trackingms.domain.model.TransportStatus;

import java.time.LocalDateTime;

/**
 * 輸送状態更新イベント（US17 / IT5 タスク 2.2）。
 *
 * <p>{@code TrackingActivity} 集約が手動更新コマンドを受理して状態を遷移させたときに発行する。
 * {@code TransportStatusTransition.canTransition} が許可した遷移のみが本イベントを生む。</p>
 *
 * <p>本イベントは {@code tracking_summary} の {@code current_status} / {@code current_unlocode} /
 * {@code current_voyage_number} / {@code last_event_at} を更新し、{@code tracking_event} に
 * {@code source = MANUAL} で履歴を 1 行追加する（IT5 タスク 2.3）。</p>
 *
 * @param trackingNumber 追跡番号
 * @param fromStatus     遷移前の状態（監査用）
 * @param toStatus       遷移後の状態
 * @param unlocode       現在の港湾コード（任意）
 * @param voyageNumber   関連する航海番号（任意）
 * @param occurredAt     状態が変化した実時刻
 * @param description    任意の説明
 */
public record TransportStatusUpdatedEvent(
        String trackingNumber,
        TransportStatus fromStatus,
        TransportStatus toStatus,
        String unlocode,
        String voyageNumber,
        LocalDateTime occurredAt,
        String description
) {
}
