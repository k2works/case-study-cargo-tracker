package com.example.trackingms.domain.model;

/**
 * 追跡例外の対応状態（US19 / US20 / domain-model.md）。
 *
 * <p>状態遷移は単方向で {@code REPORTED → RESPONDING → RESOLVED}。逆行・スキップは拒否される。
 * ただし {@code REPORTED → RESOLVED} の直接解決（軽微な例外）は許可する（plan.md 状態遷移図）。</p>
 */
public enum ResponseStatus {
    REPORTED,
    RESPONDING,
    RESOLVED;

    /**
     * 現在の状態から指定状態への遷移が許可されるか。
     *
     * <ul>
     *   <li>REPORTED → RESPONDING / RESOLVED</li>
     *   <li>RESPONDING → RESOLVED</li>
     *   <li>RESOLVED → なし（終端）</li>
     *   <li>同一状態への遷移は false（更新の意味がない）</li>
     * </ul>
     */
    public boolean canTransitionTo(ResponseStatus to) {
        if (to == null || to == this) {
            return false;
        }
        switch (this) {
            case REPORTED:
                return to == RESPONDING || to == RESOLVED;
            case RESPONDING:
                return to == RESOLVED;
            case RESOLVED:
            default:
                return false;
        }
    }
}
