package com.example.cargotracker.estimation.domain.model;

/**
 * 貨物種別（見積）。
 *
 * <p><strong>BC 固有型である</strong>（ADR-005）。共有カーネルは {@code Location} と
 * {@code ShipperId} の 2 つのみであり、貨物種別は各 BC が自分の言葉で持つ。
 * Routing の {@code RoutingCargoType}・Handling の型と同じ形である。
 *
 * <p>表示名の正典は本列挙型である。<strong>画面に対応表を書き写さない。</strong>
 */
public enum EstimationCargoType {

    /** 一般貨物。 */
    GENERAL("一般貨物"),

    /** 危険物。<strong>申告が要る</strong>（US01 の受入基準 6）。 */
    HAZARDOUS("危険物"),

    /** 冷凍・冷蔵。 */
    REFRIGERATED("冷凍冷蔵");

    private final String displayName;

    EstimationCargoType(String displayName) {
        this.displayName = displayName;
    }

    /** 画面に出す表示名。 */
    public String displayName() {
        return displayName;
    }
}
