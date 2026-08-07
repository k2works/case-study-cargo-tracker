package com.example.cargotracker.booking.domain.model;

/**
 * 貨物の経路状態（US09 / US11）。
 *
 * <p><strong>Routing Context の状態とは別の型である。</strong> 値は対応するが、
 * 「経路提案の状態」と「貨物の経路状態」は別の事実である。提案が選択済みでも、
 * 貨物への反映が失敗すれば貨物は {@link #NOT_ROUTED} のままである。
 * BC をまたいで型を共有しない（ADR-005・ArchUnit ルール 4）。
 *
 * <p>表示名は {@code ui_design.md} の付録（全 enum の日本語ラベルの正典）に揃える。
 * <strong>画面側で状態名を並べて分岐しない。</strong>
 */
public enum CargoRoutingStatus {

    /** 経路が割り当てられていない。 */
    NOT_ROUTED("未割り当て", "bg-secondary"),

    /** 経路が割り当てられている。 */
    ROUTED("割り当て済", "bg-success"),

    /** 予定と異なる経路で輸送されている（US28）。 */
    MISROUTED("誤配", "bg-danger");

    private final String displayName;
    private final String badgeClass;

    CargoRoutingStatus(String displayName, String badgeClass) {
        this.displayName = displayName;
        this.badgeClass = badgeClass;
    }

    public String displayName() {
        return displayName;
    }

    public String badgeClass() {
        return badgeClass;
    }
}
