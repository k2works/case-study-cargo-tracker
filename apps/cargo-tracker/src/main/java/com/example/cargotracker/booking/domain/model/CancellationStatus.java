package com.example.cargotracker.booking.domain.model;

/**
 * キャンセル申請の状態（US30）。
 *
 * <p><strong>輸送中のキャンセルは承認を伴う</strong>（遷移表 #10）。
 * 貨物は船の上にあり、<strong>どこで降ろすかを決めないままキャンセルすると
 * 貨物が宙に浮く</strong>。荷役の現場は行き先の無い荷物を抱えることになる。
 *
 * <p><strong>「取り消し」ではなく「キャンセル」である。</strong> US36 の
 * 引取記録の取り消し（{@code CorrectionStatus}）とは別の業務であり、
 * <strong>語を混ぜると画面で何を承認しているのか分からなくなる</strong>。
 */
public enum CancellationStatus {

    /** 承認待ち。<strong>追跡管理者の作業待ち行列である。</strong> */
    PENDING("承認待ち", "bg-warning text-dark"),

    /** 承認済み。<strong>この時点で予約がキャンセルになる。</strong> */
    APPROVED("承認済み", "bg-success"),

    /** 却下。<strong>記録は残す</strong> — 却下したことも経緯である。 */
    REJECTED("却下", "bg-secondary");

    private final String displayName;
    private final String badgeClass;

    CancellationStatus(String displayName, String badgeClass) {
        this.displayName = displayName;
        this.badgeClass = badgeClass;
    }

    /** 画面に出す日本語名。<strong>列挙子名を利用者に見せない。</strong> */
    public String displayName() {
        return displayName;
    }

    /** 画面のバッジ。<strong>正典はここである</strong> — 画面で色を決め直さない。 */
    public String badgeClass() {
        return badgeClass;
    }

    /** まだ決まっていないか。<strong>画面の出し分けは本述語をそのまま呼ぶ。</strong> */
    public boolean isPending() {
        return this == PENDING;
    }

    /** 承認済みか。 */
    public boolean isApproved() {
        return this == APPROVED;
    }
}
