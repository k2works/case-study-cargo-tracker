package com.example.cargotracker.routing.domain.model.valueobjects;

/**
 * 航海が取り扱える貨物種別。
 *
 * <p><strong>Booking の {@code CargoType} とは別の型である。</strong> 値は同じ 3 つだが
 * 意味が違う。Booking の {@code CargoType} は「この貨物は何か」、本列挙型は
 * 「この航海は何を運べるか」である。
 *
 * <p>Booking の型を参照すると BC 間の直接参照になり、ArchUnit ルール 4 で落ちる。
 * 共有カーネルに上げる案も採らない。共有カーネルは {@code Location} と
 * {@code ShipperId} の 2 要素のみである（ADR-005）。
 */
public enum RoutingCargoType {

    /** 一般貨物。 */
    GENERAL("一般貨物"),

    /** 危険物。取扱可能な港と船が限られる。 */
    HAZARDOUS("危険物"),

    /** 冷凍・冷蔵。冷凍設備を持つ船に限る。 */
    REFRIGERATED("冷凍・冷蔵");

    private final String displayName;

    RoutingCargoType(String displayName) {
        this.displayName = displayName;
    }

    /** 画面に出す日本語名（{@code ui_design.md} 付録が正典）。 */
    public String displayName() {
        return displayName;
    }
}
