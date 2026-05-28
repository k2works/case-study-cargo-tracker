package com.example.trackingms.domain.commands;

import org.axonframework.modelling.command.TargetAggregateIdentifier;

/**
 * 追跡初期化コマンド（US14 / IT5 タスク 1.3）。
 *
 * <p>bookingms の Saga が {@code BookingConfirmedEvent}（予約確定）を受けて発行する
 * {@code TrackingIssuanceRequestedEvent}（shared）を起点に、trackingms 側の Saga / 受信ハンドラが
 * 採番された {@link com.example.trackingms.domain.model.TrackingNumber}（{@code TrackingNumberGenerator}
 * で生成）を本コマンドに載せて発行する。</p>
 *
 * <p>本コマンドは {@code TrackingActivity} 集約を新規生成し、初期状態 {@code NOT_RECEIVED} で
 * 起動する（{@link com.example.trackingms.domain.events.TrackingInitializedEvent} を発行）。</p>
 *
 * @param trackingNumber 採番済み追跡番号（TRK- + 大文字英数 10 桁）
 * @param bookingId      予約識別子（Saga association）
 */
public record InitializeTrackingCommand(
        @TargetAggregateIdentifier String trackingNumber,
        String bookingId
) {
}
