package com.example.cargotracker.support;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * 「ついさっき」を日をまたがずに作れているか。
 *
 * <p><strong>実際に踏んだ欠陥を、時計を固定して再現する。</strong> 2026-08-13 の 00:09 に
 * 20 件のテストが落ちた —— {@code now.minusHours(1)} が前日に落ち、
 * 「作業日時が追跡番号の発行日より前です」という<strong>正しい拒否</strong>を受けていた。
 *
 * <p><strong>時刻に依存する欠陥は、時刻を固定しないと再現できない。</strong>
 * 実時刻で走らせるだけのテストは、1 日のうち 1 時間しか赤くならない。
 */
@DisplayName("ついさっきの作業日時（日をまたがない）")
class RecentBusinessTimeTest {

    private static final LocalDate 今日 = LocalDate.of(2026, java.time.Month.AUGUST, 13);

    private static Clock 時計(String instant) {
        return Clock.fixed(Instant.parse(instant), ZoneOffset.UTC);
    }

    @Test
    void 日中は素直に何時間か前を返す() {
        assertThat(RecentBusinessTime.hoursAgo(時計("2026-08-13T10:30:00Z"), 1))
                .isEqualTo(LocalDateTime.of(2026, java.time.Month.AUGUST, 13, 9, 30));
        assertThat(RecentBusinessTime.hoursAgo(時計("2026-08-13T10:30:00Z"), 2))
                .isEqualTo(LocalDateTime.of(2026, java.time.Month.AUGUST, 13, 8, 30));
    }

    /** <strong>これが 20 件を落とした条件である。</strong> */
    @Test
    void 真夜中直後でも前日に落ちない() {
        assertThat(RecentBusinessTime.hoursAgo(時計("2026-08-13T00:09:00Z"), 1).toLocalDate())
                .isEqualTo(今日);
        assertThat(RecentBusinessTime.hoursAgo(時計("2026-08-13T00:09:00Z"), 2).toLocalDate())
                .isEqualTo(今日);
    }

    /** <strong>未来の作業日時も作らない。</strong> 未来の荷役は別の理由で拒まれる。 */
    @Test
    void いまより後にはならない() {
        Clock clock = 時計("2026-08-13T00:09:00Z");

        assertThat(RecentBusinessTime.hoursAgo(clock, 1))
                .isBeforeOrEqualTo(LocalDateTime.now(clock));
    }

    /**
     * <strong>「n 時間前」は真夜中の近くで同時刻になりうる</strong>（クローズ前レビュー H4）。
     *
     * <p>初版はここで順序を保とうとして時間の代わりに分だけ戻し、
     * <strong>01:00 で順序を逆転させた</strong>（1 時間前が 00:00、2 時間前が 00:58）。
     *
     * <p><strong>今日の中に収める以上、複数の値が 00:00 に集まるのは避けられない。</strong>
     * ここで固定するのは「逆転しないこと」であり、「必ず相異なること」ではない。
     * 前後関係が要るなら {@link RecentBusinessTime#ordered} を使う。
     */
    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {
        "2026-08-13T00:00:00Z", "2026-08-13T00:00:30Z", "2026-08-13T00:01:00Z",
        "2026-08-13T00:02:00Z", "2026-08-13T00:09:00Z", "2026-08-13T01:00:00Z",
        "2026-08-13T10:30:00Z"})
    void 何時間前は逆転しないし日もまたがない(String instant) {
        Clock clock = 時計(instant);

        assertThat(RecentBusinessTime.hoursAgo(clock, 2))
                .as("**2 時間前が 1 時間前より新しくならない**")
                .isBeforeOrEqualTo(RecentBusinessTime.hoursAgo(clock, 1));
        assertThat(RecentBusinessTime.hoursAgo(clock, 2).toLocalDate())
                .as("**日をまたがない**")
                .isEqualTo(今日);
        assertThat(RecentBusinessTime.hoursAgo(clock, 1))
                .as("**未来にならない**")
                .isBeforeOrEqualTo(LocalDateTime.now(clock));
    }

    /**
     * <strong>順番が要るときは相異なる時刻を返す</strong>（クローズ前レビュー H4）。
     *
     * <p>荷役は「受領 → 積込」のように順に起きる。同時刻だと
     * <strong>並び順を確かめるテストが何も判別しなくなる</strong>。
     */
    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {
        "2026-08-13T00:02:00Z", "2026-08-13T00:09:00Z", "2026-08-13T01:00:00Z",
        "2026-08-13T10:30:00Z"})
    void 順番が要るときは相異なる時刻を古い順に返す(String instant) {
        Clock clock = 時計(instant);

        var times = RecentBusinessTime.ordered(clock, 3);

        assertThat(times).hasSize(3);
        assertThat(times).isSorted();
        assertThat(times).doesNotHaveDuplicates();
        assertThat(times).allSatisfy(t -> {
            assertThat(t.toLocalDate()).as("**日をまたがない**").isEqualTo(今日);
            assertThat(t).as("**未来にならない**").isBeforeOrEqualTo(LocalDateTime.now(clock));
        });
    }

    /**
     * <strong>00:00 ちょうどだけは詰めようが無い。</strong>
     *
     * <p>ここで嘘をつかないために、<strong>相異ならないことを固定しておく</strong>。
     */
    @Test
    void ちょうど真夜中では順番を付けられない() {
        var times = RecentBusinessTime.ordered(時計("2026-08-13T00:00:00Z"), 3);

        assertThat(times).containsOnly(今日.atStartOfDay());
    }

    /** ちょうど 00:00 は丸める先が無い。<strong>今日の 00:00 を返す。</strong> */
    @Test
    void ちょうど真夜中では今日の始まりを返す() {
        assertThat(RecentBusinessTime.hoursAgo(時計("2026-08-13T00:00:00Z"), 1))
                .isEqualTo(今日.atStartOfDay());
    }
}
