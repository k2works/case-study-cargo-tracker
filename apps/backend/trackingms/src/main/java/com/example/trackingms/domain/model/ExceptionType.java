package com.example.trackingms.domain.model;

/**
 * 追跡例外の種別（US19 / US20 / domain-model.md）。
 *
 * <ul>
 *   <li>{@link #DELAY}：遅延（US19）— 悪天候・港湾混雑・通関遅延など</li>
 *   <li>{@link #DAMAGE}：破損（US20）— 外装・コンテナ・内容物の損傷</li>
 *   <li>{@link #LOSS}：紛失（US20）— 重大例外、自動 escalation</li>
 * </ul>
 */
public enum ExceptionType {
    DELAY,
    DAMAGE,
    LOSS;

    /**
     * 管理職への escalation 通知が必要な種別か（US20 受入基準 3）。
     */
    public boolean isEscalationRequired() {
        return this == LOSS;
    }
}
