package com.example.cargotracker.billing.domain.model.valueobjects;

/**
 * 輸送料金の状態（US21）。
 *
 * <p><strong>{@code PaymentStatus} を流用しない。</strong> あちらは支払いの状態であり、
 * こちらは料金そのものの状態である。1 つにまとめると
 * <strong>「料金は確定したが未入金」と「料金が未確定」が同じ {@code PENDING} になり、
 * 督促の対象を選べなくなる</strong>（US23 の受入基準「支払い期限超過時に未払い通知」）。
 */
public enum ChargeStatus {

    /** 下書き。**経理担当者が確認している最中である。** */
    DRAFT("下書き", "bg-secondary"),

    /** 確定。**この時点で金額は動かない。** */
    CONFIRMED("確定", "bg-success");

    private final String displayName;
    private final String badgeClass;

    ChargeStatus(String displayName, String badgeClass) {
        this.displayName = displayName;
        this.badgeClass = badgeClass;
    }

    /** 画面に出す日本語名。**列挙子名を利用者に見せない。** */
    public String displayName() {
        return displayName;
    }

    /** 画面のバッジ。**正典はここである** — 画面で色を決め直さない。 */
    public String badgeClass() {
        return badgeClass;
    }

    /** 確定済みか。**画面の出し分けは本述語をそのまま呼ぶ。** */
    public boolean isConfirmed() {
        return this == CONFIRMED;
    }
}
