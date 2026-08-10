package com.example.cargotracker.billing.domain.model;

/**
 * 支払いの状態（US23）。
 *
 * <p><strong>{@code ChargeStatus} と別の軸である</strong>（ADR-017）。
 * 1 つにまとめると「料金は確定したが未入金」と「料金が未確定」が
 * 同じ状態になり、<strong>督促の対象を選べなくなる</strong>。
 *
 * <p><strong>期限超過は自動では動かない。</strong> 画面を開いたときに
 * 日付で判定する。夜間バッチにすると、動いているかを誰も確かめない。
 *
 * <p><strong>{@code REFUNDED} は本 IT では作らない。</strong> 返金は
 * 精算の取り消しを伴う別の業務であり、要求元がない状態で状態だけ足すと
 * 「到達できない状態」が表に残る。
 */
public enum PaymentStatus {

    /** 未入金。**発行した直後はここである。** */
    PENDING("未入金", "bg-warning text-dark"),

    /** 入金確認済。**この時点で予約は精算済になる。** */
    CONFIRMED("入金確認済", "bg-success"),

    /** 支払期限超過。**督促の対象である。** */
    OVERDUE("支払期限超過", "bg-danger");

    private final String displayName;
    private final String badgeClass;

    PaymentStatus(String displayName, String badgeClass) {
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

    /** 入金が済んでいるか。<strong>画面の出し分けは本述語をそのまま呼ぶ。</strong> */
    public boolean isPaid() {
        return this == CONFIRMED;
    }

    /**
     * 入金を待っている状態か（<strong>期限超過を含む</strong>）。
     *
     * <p><strong>遅れても入金は入金である。</strong> 期限を過ぎた請求書に
     * 入金確認のボタンが出ないと、入金があったのに記録できない。
     */
    public boolean awaitingPayment() {
        return this == PENDING || this == OVERDUE;
    }

    /** 督促の対象か。 */
    public boolean isOverdue() {
        return this == OVERDUE;
    }

    /** 名前から引く。<strong>読めない値は既定へ寄せない。</strong> */
    public static PaymentStatus of(String name) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("支払いの状態は必須です");
        }
        return valueOf(name.strip());
    }
}
