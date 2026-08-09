package com.example.cargotracker.handling.domain.model;

/**
 * 訂正・取り消し申請の状態（US36）。
 *
 * <p><strong>追跡管理者の承認なしには状態が戻らない。</strong> 現場が自分で
 * 取り消せると、引き渡しの証明（US35）が現場の判断で消せることになる。
 */
public enum CorrectionStatus {

    /** 承認待ち。**追跡管理者の作業待ち行列である。** */
    PENDING("承認待ち", "bg-warning text-dark"),

    /** 承認済み。**この時点で貨物状態が戻る（取り消しの場合）。** */
    APPROVED("承認済み", "bg-success"),

    /** 却下。**記録は残す** — 却下したことも経緯である。 */
    REJECTED("却下", "bg-secondary");

    private final String displayName;
    private final String badgeClass;

    CorrectionStatus(String displayName, String badgeClass) {
        this.displayName = displayName;
        this.badgeClass = badgeClass;
    }

    public String displayName() {
        return displayName;
    }

    public String badgeClass() {
        return badgeClass;
    }

    /** まだ決まっていないか。**画面の出し分けは本述語をそのまま呼ぶ。** */
    public boolean isPending() {
        return this == PENDING;
    }
}
