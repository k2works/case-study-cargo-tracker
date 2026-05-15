package com.example.cargotracker.bookingms.domain.model.events;

/**
 * 予約引き渡し完了イベント（US06 / UC04）。
 *
 * <p>{@code Cargo} 集約が {@code HandOffToRoutingCommand} を処理して発行する。
 * {@code CargoProjectionsEventHandler} が {@code cargo_summary.booking_status} を
 * {@code ROUTING} に更新する。下流の経路設計者への通知は Saga / Notification ACL で扱う
 * （IT3 では最小実装としてログ出力のみ）。</p>
 */
public record CargoHandedOffToRoutingEvent(String bookingId) {
}
