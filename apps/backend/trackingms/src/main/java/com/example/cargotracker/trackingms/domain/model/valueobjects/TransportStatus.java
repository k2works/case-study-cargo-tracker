package com.example.cargotracker.trackingms.domain.model.valueobjects;

/**
 * 貨物の輸送状態。{@code iteration_plan-6.md} の状態遷移図に準拠。
 *
 * <p>遷移は以下の経路を想定する:</p>
 *
 * <pre>
 *   [初期]→ NOT_RECEIVED → RECEIVED → LOADED → IN_TRANSIT
 *                                            ↕ UNLOADED → IN_TRANSIT（次レグ）
 *                                            UNLOADED → AWAITING_CLAIM → DELIVERED [終了]
 *
 *   IN_TRANSIT / AWAITING_CLAIM → MISROUTED / EXCEPTION（特殊状態）
 * </pre>
 */
public enum TransportStatus {
    NOT_RECEIVED,
    RECEIVED,
    LOADED,
    IN_TRANSIT,
    UNLOADED,
    AWAITING_CLAIM,
    DELIVERED,
    MISROUTED,
    EXCEPTION;

    /**
     * 終端状態か（DELIVERED のみ）。これより先の状態遷移は発生しない。
     */
    public boolean isTerminal() {
        return this == DELIVERED;
    }
}
