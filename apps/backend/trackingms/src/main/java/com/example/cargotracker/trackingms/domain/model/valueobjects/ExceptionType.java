package com.example.cargotracker.trackingms.domain.model.valueobjects;

/**
 * 追跡例外の種別を表す列挙型（US19）。
 *
 * <p>{@code LOSS} はエスカレーション対象（{@link #isEscalated()} が {@code true}）として
 * 管理者への WARN ログを出力する。</p>
 */
public enum ExceptionType {
    DELAY(false),
    DAMAGE(false),
    LOSS(true);

    private final boolean escalated;

    ExceptionType(boolean escalated) {
        this.escalated = escalated;
    }

    public boolean isEscalated() {
        return escalated;
    }
}
