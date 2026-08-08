package com.example.cargotracker.booking.domain.model;

/**
 * 荷主への通知の種別（US12）。
 *
 * <p>表示名は列挙子自身が持つ。<strong>画面側に対応表を書き写さない。</strong>
 * 種別を増やしたときに片方だけが更新される形を作らない。
 */
public enum NotificationType {

    /** 経路確定の通知（US12）。 */
    ROUTE_CONFIRMED("経路確定"),

    /** スケジュール変更の通知（US25 で使う）。 */
    SCHEDULE_CHANGED("スケジュール変更"),

    /** 例外発生の通知（US19 / US20 で使う）。 */
    EXCEPTION_RAISED("例外発生"),

    /**
     * 貨物状態の更新（US17）。
     *
     * <p>追跡管理者が手で状態を進めたことを荷主に知らせる記録である。
     * <strong>状態が動いたときだけ作る</strong>（入港のように動かない更新では作らない）。
     */
    STATUS_UPDATED("状態更新");

    private final String displayName;

    NotificationType(String displayName) {
        this.displayName = displayName;
    }

    public String displayName() {
        return displayName;
    }
}
