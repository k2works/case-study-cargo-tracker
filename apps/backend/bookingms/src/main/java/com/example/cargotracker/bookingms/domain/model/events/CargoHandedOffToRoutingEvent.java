package com.example.cargotracker.bookingms.domain.model.events;

import org.axonframework.eventsourcing.annotation.EventTag;

/**
 * 予約引き渡し完了イベント（US06 / UC04）。
 *
 * <p>{@code Cargo} 集約が {@code HandOffToRoutingCommand} を処理して発行する。
 * {@code CargoProjectionsEventHandler} が {@code cargo_summary.booking_status} を
 * {@code ROUTING} に更新する。下流の経路設計者への通知は Saga / Notification ACL で扱う
 * （IT3 では最小実装としてログ出力のみ）。</p>
 *
 * <p>{@code @EventTag} により {@code bookingId} を tag として記録し、後続コマンドが
 * 同一集約に対するイベント列を読み戻せるようにする。</p>
 */
public record CargoHandedOffToRoutingEvent(@EventTag String bookingId) {
}
