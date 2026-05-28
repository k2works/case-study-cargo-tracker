package com.example.bookingms.domain.commands;

import org.axonframework.modelling.command.TargetAggregateIdentifier;

/**
 * 追跡情報割当コマンド（US14 / IT5 タスク 1.4）。
 *
 * <p>{@code BookingSagaManager} が trackingms から
 * {@code com.example.shared.events.CargoTrackedEvent}（採番完了通知）を受信した際に、
 * Cargo 集約に対して送信する。Cargo 集約は予約状態を CONFIRMED から TRACKING_ISSUED へ遷移し、
 * 採番された追跡番号を保持する（{@code CargoTrackingAssignedEvent} を発行）。</p>
 *
 * <p>Cargo 集約の不変条件: 本コマンドは {@code bookingStatus = CONFIRMED} のときのみ受理する。
 * それ以外の状態（PRELIMINARY / ROUTING / ROUTE_PROPOSED / TRACKING_ISSUED 以降）では拒否する。</p>
 */
public record AssignTrackingDetailsCommand(
        @TargetAggregateIdentifier String bookingId,
        String trackingNumber
) {
}
