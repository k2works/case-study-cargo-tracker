package com.example.cargotracker.booking.domain.model;

/**
 * 貨物種別。
 *
 * <p>DB の {@code chk_cargo_type} と対応する。危険物・冷凍は取扱可能港の制約を持つ
 * （ビジネスルール 6）が、その判定は Routing Context の責務であり本 IT の対象外である。
 */
public enum CargoType {

    /** 一般貨物。 */
    GENERAL("一般貨物"),

    /** 危険物。{@code HazardousDeclaration} が必須（US05）。 */
    HAZARDOUS("危険物"),

    /** 冷凍・冷蔵。{@code TemperatureRequirement} が必須（US05）。 */
    REFRIGERATED("冷凍・冷蔵");

    private final String displayName;

    CargoType(String displayName) {
        this.displayName = displayName;
    }

    /** 画面・帳票に出す日本語名。**列挙子名を利用者に見せない**。 */
    public String displayName() {
        return displayName;
    }
}
