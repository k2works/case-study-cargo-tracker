package com.example.cargotracker.tracking.domain.model;

/**
 * 追跡イベントの出どころ（US17）。
 *
 * <p><strong>荷役由来と手で入れたものを区別する。</strong> 混ぜたままにすると
 * 「誰がいつ手で入れたか」を後から追えない。手動更新は業務の判断であり、
 * 現場の記録とは重みが違う。
 */
public enum TrackingEventSource {

    /** 荷役の登録から反映されたもの（US15 / US16）。 */
    HANDLING("荷役"),

    /** 追跡管理者が手で入れたもの（US17）。 */
    MANUAL("手動");

    private final String displayName;

    TrackingEventSource(String displayName) {
        this.displayName = displayName;
    }

    public String displayName() {
        return displayName;
    }
}
