package com.example.trackingms.domain.events;

import java.time.LocalDateTime;

/**
 * 誤配送検知イベント（US17 / IT5 タスク 2.2）。
 *
 * <p>{@code TrackingActivity} の状態が {@code MISROUTED} へ遷移したときに
 * {@code TransportStatusUpdatedEvent} と同時に発行される。{@code tracking_summary.misrouted}
 * フラグを {@code true} に立てる投影トリガー（IT5 タスク 2.3）。</p>
 *
 * <p>本イベントは将来 IT6 以降で例外管理（US19/US20）や通知（NotificationAcl）の
 * トリガーとしても利用される。</p>
 *
 * @param trackingNumber 追跡番号
 * @param unlocode       誤配送が検知された港湾コード（任意）
 * @param occurredAt     誤配送検知時刻
 */
public record CargoMisroutedEvent(
        String trackingNumber,
        String unlocode,
        LocalDateTime occurredAt
) {
}
