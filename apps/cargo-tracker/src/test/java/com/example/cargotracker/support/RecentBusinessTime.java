package com.example.cargotracker.support;

import java.time.Clock;
import java.time.LocalDateTime;

/**
 * 「ついさっき」を表す作業日時を、<strong>日をまたがずに</strong>作る。
 *
 * <p><strong>{@code LocalDateTime.now(clock).minusHours(1)} は真夜中に壊れる。</strong>
 * 業務のタイムゾーンで 00:00〜00:59 に走らせると前日に落ち、
 * 「作業日時が追跡番号の発行日（今日）より前です」という<strong>正しい拒否</strong>を
 * 受ける。テストは赤くなるが、赤いのはテストの作り方であって実装ではない。
 *
 * <p><strong>実際に踏んだ</strong>（IT20 の品質ゲート。2026-08-13 00:09 に 20 件が落ちた）。
 * 6 か所に同じ書き方が散っていたため、ここへ寄せた ——
 * <strong>同じ間違いを 6 回書けるなら、7 回目も書ける。</strong>
 *
 * <p>返すのは「今日の範囲に収まる、いまより前の時刻」である。
 * 今日の 00:00 は<strong>発行日より前ではなく、未来でもない</strong>。
 */
public final class RecentBusinessTime {

    private RecentBusinessTime() {
    }

    /**
     * いまより {@code hoursAgo} 時間前。<strong>ただし今日の 00:00 より前には戻さない。</strong>
     *
     * @param clock    業務のタイムゾーンを持つ時計（Spring が注入するもの）
     * @param hoursAgo 何時間前か
     */
    public static LocalDateTime hoursAgo(Clock clock, int hoursAgo) {
        LocalDateTime now = LocalDateTime.now(clock).withSecond(0).withNano(0);
        LocalDateTime startOfToday = now.toLocalDate().atStartOfDay();

        LocalDateTime shifted = now.minusHours(hoursAgo);
        if (!shifted.isBefore(startOfToday)) {
            return shifted;
        }
        // **前後関係は保つ。** 荷役は「2 時間前 → 1 時間前」の順に登録することがあり、
        // 一律に 00:00 へ丸めると**同時刻になって順序が消える**。
        // 日をまたぐ時間帯だけ、時間の代わりに分だけ戻す
        LocalDateTime narrowed = now.minusMinutes(hoursAgo);
        return narrowed.isBefore(startOfToday) ? startOfToday : narrowed;
    }

    /** {@link #hoursAgo} を画面のパラメータに載せる形（{@code yyyy-MM-ddTHH:mm}）。 */
    public static String hoursAgoText(Clock clock, int hoursAgo) {
        return hoursAgo(clock, hoursAgo).toString();
    }
}
