package com.example.bookingms.domain.model;

/**
 * 経路設定状態。
 *
 * <p>US04 で NOT_ROUTED が初期状態。US09 経路確定で ROUTED へ遷移。</p>
 */
public enum RoutingStatus {
    NOT_ROUTED,
    ROUTED,
    MISROUTED
}
