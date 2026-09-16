package com.example.cargotracker.acceptance.routing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import com.example.cargotracker.shared.infrastructure.time.BusinessClockConfiguration;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.concurrent.Callable;

/** 「N 秒以内に」を 1 か所に閉じる（bookingms 側の SharedSteps と同じ形）。 */
final class SharedRoutingSteps {

    /**
     * フィクスチャの日時は<b>「今」から導く</b>。
     *
     * <p><b>固定日付にすると時限式になる。</b> 一覧は既定で出港済みを外すので、
     * 書いた日付を現実の時刻が追い越した瞬間に、登録した航海が一覧から消える
     * ——「登録した航海が 10 秒以内に出る」が全滅する。IT12 で実際に起きた：
     * 出発日時が {@code 2026-09-10T09:00:00Z} 固定で、同じ日の 08:00 UTC の CI は
     * 緑、09:51 UTC の CI は 13 件が赤になった。日付そのものが検査したい対象では
     * ないので、常に未来になるよう数える。</p>
     */
    static final Instant DEPARTURE = departureBase();

    /** 多区間の 1 区間目の到着。 */
    static final Instant MID_ARRIVAL = DEPARTURE.plus(6, ChronoUnit.DAYS);

    /** 多区間の 2 区間目の出発（1 区間目の到着より後）。 */
    static final Instant MID_DEPARTURE = DEPARTURE.plus(7, ChronoUnit.DAYS);

    /** 最終到着。 */
    static final Instant ARRIVAL = DEPARTURE.plus(14, ChronoUnit.DAYS);

    private SharedRoutingSteps() {
    }

    /**
     * 出発は<b>業務日付の翌週の 09:00</b>（業務タイムゾーン）。
     *
     * <p>翌週にするのは、寄港地を足したり期間で絞ったりする分の幅を前後に
     * 取るためである。業務タイムゾーンで数えるのは、UTC で数えると時差の分だけ
     * 「当日」の境界がずれるため（IT8 の教訓）。</p>
     */
    private static Instant departureBase() {
        return LocalDate.now(BusinessClockConfiguration.BUSINESS_ZONE)
                .plusDays(7)
                .atTime(9, 0)
                .atZone(BusinessClockConfiguration.BUSINESS_ZONE)
                .toInstant();
    }

    /** 出発日（業務タイムゾーン）。出発期間の絞り込みを組むときに使う。 */
    static LocalDate departureDate() {
        return LocalDate.ofInstant(DEPARTURE, BusinessClockConfiguration.BUSINESS_ZONE);
    }

    /** ISO 8601（UTC）の文字列。API はこの形で受ける。 */
    static String iso(Instant at) {
        return at.toString();
    }

    static void awaitWithin(int seconds, Callable<Boolean> condition, String description) {
        awaitWithin(seconds, condition, description, true);
    }

    /**
     * 条件が起きるのを待つ。
     *
     * <p>{@code expected} を false にすると「その時間のあいだ起きないこと」を
     * 確かめる。<b>「変わらない」は待たなければ判別できない。</b> 投影は非同期なので、
     * 直後に読んだ古い値は「変わっていない」と区別が付かない。</p>
     */
    static void awaitWithin(int seconds, Callable<Boolean> condition, String description,
            boolean expected) {
        if (!expected) {
            try {
                await(description)
                        .atMost(Duration.ofSeconds(seconds))
                        .pollInterval(Duration.ofMillis(300))
                        .until(condition);
            } catch (org.awaitility.core.ConditionTimeoutException e) {
                return;
            }
            assertThat(false).as("%s（起きてはいけないことが起きた）", description).isTrue();
            return;
        }
        try {
            await(description)
                    .atMost(Duration.ofSeconds(seconds))
                    .pollInterval(Duration.ofMillis(300))
                    .until(condition);
        } catch (org.awaitility.core.ConditionTimeoutException e) {
            assertThat(false).as("%s（%d 秒以内に起きなかった）", description, seconds).isTrue();
        }
    }
}
