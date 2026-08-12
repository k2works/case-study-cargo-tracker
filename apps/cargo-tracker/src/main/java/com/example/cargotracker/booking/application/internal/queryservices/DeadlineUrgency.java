package com.example.cargotracker.booking.application.internal.queryservices;

/**
 * 希望期限までの残り日数を、画面の色に写す規則（{@code ui_design.md}）。
 *
 * <p><strong>3 日以内は赤、7 日以内は橙。</strong> 経路設計者が朝に見るのは
 * 「どれが一番切羽詰まっているか」であり、日付の数字だけでは一目で判断できない。
 * 期限を過ぎたものも赤で示す（見落としが最も痛い）。
 *
 * <p><strong>これは規則であって問い合わせではない</strong>（ADR-022）。以前は
 * MyBatis のクエリサービスの中にあった。しきい値が infrastructure にあると、
 * <strong>同じ数字が画面ごとに散り、規則を壊すテストを書く場所も無くなる</strong>。
 *
 * <p><strong>画面では判断しない。</strong> テンプレートで日数を比べて分岐すると、
 * 一覧ごとに閾値がずれる。
 */
public final class DeadlineUrgency {

    /** 赤にする残り日数。 */
    private static final long CRITICAL_DAYS = 3;

    /** 橙にする残り日数。 */
    private static final long WARNING_DAYS = 7;

    private DeadlineUrgency() {
    }

    /**
     * 残り日数に応じた文字色のクラス。
     *
     * @param daysUntilDeadline 希望期限までの残り日数（過ぎていれば負）
     * @return Bootstrap のクラス。急がないなら空文字
     */
    public static String classOf(long daysUntilDeadline) {
        if (daysUntilDeadline <= CRITICAL_DAYS) {
            return "text-danger fw-bold";
        }
        if (daysUntilDeadline <= WARNING_DAYS) {
            return "text-warning-emphasis fw-bold";
        }
        return "";
    }
}
