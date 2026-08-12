package com.example.cargotracker.support;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.IntStream;

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
 * <h2>「何時間前」と「順番」は別の要求である</h2>
 *
 * <p>初版は {@link #hoursAgo} だけを持ち、真夜中に丸めるときも順序を保とうとして
 * 時間の代わりに分だけ戻していた。<strong>これは 01:00 で順序を逆転させた</strong>
 * （1 時間前は 00:00 へ丸められ、2 時間前は 00:58 になる。クローズ前レビュー H4）。
 *
 * <p><strong>真夜中の近くで「n 時間前」の順序を保つことは原理的にできない。</strong>
 * 今日の中に収める以上、複数の値が {@code 00:00} に集まるためである。
 * そこで<strong>要求を分けた</strong> ——
 *
 * <ul>
 *   <li>{@link #hoursAgo}: 「ついさっき」が欲しいだけのとき。
 *       <strong>順序は保証しない</strong>（同時刻になりうる）</li>
 *   <li>{@link #ordered}: <strong>前後関係が要る</strong>とき（荷役を古い順に登録する等）。
 *       今日の中に収まる、<strong>相異なる時刻</strong>を古い順に返す</li>
 * </ul>
 */
public final class RecentBusinessTime {

    private RecentBusinessTime() {
    }

    /**
     * いまより {@code hoursAgo} 時間前。<strong>ただし今日の 00:00 より前には戻さない。</strong>
     *
     * <p><strong>順序は保証しない。</strong> 真夜中の近くでは複数の値が {@code 00:00} に
     * 集まる。前後関係が要るなら {@link #ordered} を使うこと。
     *
     * @param clock    業務のタイムゾーンを持つ時計（Spring が注入するもの）
     * @param hoursAgo 何時間前か
     */
    public static LocalDateTime hoursAgo(Clock clock, int hoursAgo) {
        LocalDateTime now = LocalDateTime.now(clock).withSecond(0).withNano(0);
        LocalDateTime startOfToday = now.toLocalDate().atStartOfDay();
        LocalDateTime shifted = now.minusHours(hoursAgo);
        return shifted.isBefore(startOfToday) ? startOfToday : shifted;
    }

    /** {@link #hoursAgo} を画面のパラメータに載せる形（{@code yyyy-MM-ddTHH:mm}）。 */
    public static String hoursAgoText(Clock clock, int hoursAgo) {
        return hoursAgo(clock, hoursAgo).toString();
    }

    /**
     * 今日の中に収まる、<strong>相異なる時刻を古い順に</strong> {@code count} 個。
     *
     * <p>荷役は「受領 → 積込」のように順に起きる。同時刻だと
     * <strong>並び順を確かめるテストが何も判別しなくなる</strong>。
     *
     * <p>間隔は「今日が始まってから経過した時間」を等分して決める ——
     * 真夜中直後は分刻み、日中は時間刻みになる。
     * <strong>いまより後にはならず、前日にも落ちない。</strong>
     *
     * <p><strong>今日が始まって {@code count - 1} 分も経っていなければ、
     * 詰めようが無く重なる</strong>（00:00 ちょうどが極端な例）。嘘をつかないために書いておく。
     */
    public static List<LocalDateTime> ordered(Clock clock, int count) {
        if (count < 1) {
            throw new IllegalArgumentException("1 個以上を求めること: " + count);
        }
        LocalDateTime now = LocalDateTime.now(clock).withSecond(0).withNano(0);
        LocalDateTime startOfToday = now.toLocalDate().atStartOfDay();

        long elapsed = Duration.between(startOfToday, now).toMinutes();
        // 間隔は「経過時間 ÷ 隙間の数」。隙間は count - 1 個であり count 個ではない
        // （3 個なら 00:00 / 00:01 / 00:02 の 2 隙間。ここを count で割ると
        // **00:02 の時点で 3 個すべてが重なる**）
        long step = count == 1 ? 0L : Math.min(60L, elapsed / (count - 1));

        return IntStream.range(0, count)
                .mapToObj(i -> now.minusMinutes(step * (count - 1 - i)))
                .toList();
    }

    /** {@link #ordered} を画面のパラメータに載せる形。 */
    public static List<String> orderedText(Clock clock, int count) {
        return ordered(clock, count).stream().map(LocalDateTime::toString).toList();
    }
}
