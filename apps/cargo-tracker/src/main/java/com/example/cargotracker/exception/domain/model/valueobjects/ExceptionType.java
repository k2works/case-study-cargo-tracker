package com.example.cargotracker.exception.domain.model.valueobjects;

/**
 * 貨物例外種別。
 * LOSS（紛失）は緊急対応が必要なため、urgent フラグが自動設定される。
 */
public enum ExceptionType {
    /** 遅延 */
    DELAY("遅延"),
    /** 破損 */
    DAMAGE("破損"),
    /** 紛失 */
    LOSS("紛失");

    private final String displayName;

    ExceptionType(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }

    /** 緊急対応が必要な種別かどうかを返す。LOSS のみ true。 */
    public boolean isUrgent() {
        return this == LOSS;
    }
}
