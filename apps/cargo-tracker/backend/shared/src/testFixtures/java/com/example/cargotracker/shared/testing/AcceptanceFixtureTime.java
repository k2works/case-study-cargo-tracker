package com.example.cargotracker.shared.testing;

import com.example.cargotracker.shared.infrastructure.time.BusinessClockConfiguration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;

/**
 * 受け入れフィクスチャの日時を「今」から導く（IT13 負債枠 1）。
 *
 * <p><b>固定日付は時限式になる。</b> 一覧は既定で出港済みを外し、予約は過ぎた
 * 到着期限を断る。書いた日付を現実の時刻が追い越した瞬間に、直していないのに
 * 赤くなる（IT12 で 13 件）。日付そのものは検査したい対象ではないので、
 * <b>常に同じ位置関係になるよう数える</b>。</p>
 *
 * <p><b>業務タイムゾーンで数える。</b> UTC で数えると時差の分だけ「当日」の境界が
 * ずれる（IT8 の教訓）。CI は UTC で回るので、ここを間違えると CI だけが落ちる。</p>
 *
 * <p><b>書き方をここ 1 か所に置く。</b> 各ステップ定義で数え方を書くと、片方だけ
 * 直すことになる。{@code AcceptanceFixturesAreNotTimeBombsTest} は、この置き場
 * 以外での日付リテラルを赤にする。</p>
 */
public final class AcceptanceFixtureTime {

    public static final ZoneId BUSINESS_ZONE = BusinessClockConfiguration.BUSINESS_ZONE;

    private AcceptanceFixtureTime() {
    }

    /** 業務の「今日」。<b>UTC の今日ではない</b>（CI は UTC で回る）。 */
    public static LocalDate today() {
        return LocalDate.now(BUSINESS_ZONE);
    }

    /** 業務日付の {@code days} 日後（負なら前）の {@code hour} 時（業務タイムゾーン）。 */
    public static Instant at(long days, int hour) {
        return today().plusDays(days).atTime(hour, 0).atZone(BUSINESS_ZONE).toInstant();
    }

    /** 業務日付の {@code days} 日後（負なら前）。 */
    public static LocalDate date(long days) {
        return today().plusDays(days);
    }

    /** 業務日付の {@code days} 日後の {@code YYYY-MM-DD}（API はこの形で受ける）。 */
    public static String isoDate(long days) {
        return date(days).toString();
    }

    /**
     * <b>十分に過去</b>の時刻。出港済みの航海のように「もう過ぎている」ことが
     * 検査の対象である場面で使う。
     *
     * <p>literal で {@code 2020-01-01} と書いても同じだが、書けるようにすると
     * 「未来のつもりの literal」も混ざる。導く形に揃えておく。</p>
     */
    public static Instant longPast() {
        return at(-365 * 3L, 0);
    }

    /**
     * シナリオが書いた相対の日時（{@code "2 日前"} / {@code "10 日後"}）を解く。
     *
     * <p><b>シナリオにも固定日付を書かせない。</b> 業務の例として読ませたいのは
     * 「申告は数日前に出した」という位置関係であって、暦の上の 1 日ではない。
     * 時刻は業務タイムゾーンの 9 時に揃える（申告は日中の出来事）。</p>
     */
    public static Instant resolve(String relative) {
        java.util.regex.Matcher matcher =
                java.util.regex.Pattern.compile("^\\s*(\\d+)\\s*日(前|後)\\s*$").matcher(relative);
        if (!matcher.matches()) {
            throw new IllegalArgumentException(
                    "シナリオの日時は「N 日前」「N 日後」で書く（固定日付は時限式になる）: " + relative);
        }
        long days = Long.parseLong(matcher.group(1));
        return at("前".equals(matcher.group(2)) ? -days : days, 9);
    }

    /** 十分に過去の {@code YYYY-MM-DD}。 */
    public static String longPastDate() {
        return isoDate(-365 * 3L);
    }
}
