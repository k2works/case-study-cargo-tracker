package com.example.cargotracker.exception.domain.model.valueobjects;

/**
 * 貨物例外種別。
 * LOSS（紛失）は緊急対応が必要なため、urgent フラグが自動設定される。
 */
public enum ExceptionType {
    /** 遅延 */
    DELAY("遅延", "badge bg-warning text-dark"),
    /** 破損 */
    DAMAGE("破損", "badge bg-danger bg-opacity-75"),
    /** 紛失 */
    LOSS("紛失", "badge bg-danger fw-bold");

    private final String displayName;
    private final String badgeClass;

    ExceptionType(String displayName, String badgeClass) {
        this.displayName = displayName;
        this.badgeClass = badgeClass;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getBadgeClass() {
        return badgeClass;
    }

    /** 緊急対応が必要な種別かどうかを返す。LOSS のみ true。 */
    public boolean isUrgent() {
        return this == LOSS;
    }
}
