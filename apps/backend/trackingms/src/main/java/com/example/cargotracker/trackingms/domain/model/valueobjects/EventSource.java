package com.example.cargotracker.trackingms.domain.model.valueobjects;

/**
 * 追跡イベントの発生源を識別する区分。
 *
 * <ul>
 *   <li>{@link #HANDLING}: 荷役作業（handlingms の {@code HandlingActivityRegisteredEvent} 経由）</li>
 *   <li>{@link #MANUAL}:   管理者による手動更新（US17 の {@code TransportStatusUpdatedEvent}）</li>
 *   <li>{@link #SYSTEM}:   システムが自動生成（初期化・自動再ルーティング等）</li>
 * </ul>
 */
public enum EventSource {
    HANDLING,
    MANUAL,
    SYSTEM
}
