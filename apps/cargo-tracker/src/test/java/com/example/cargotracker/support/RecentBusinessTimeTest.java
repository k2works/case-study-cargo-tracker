package com.example.cargotracker.support;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

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
     * <strong>前後関係は保つ。</strong>
     *
     * <p>荷役は「2 時間前 → 1 時間前」の順に登録することがある。
     * 一律に 00:00 へ丸めると<strong>同時刻になって順序が消える</strong>。
     */
    @Test
    void 真夜中直後でも古いほうが先になる() {
        Clock clock = 時計("2026-08-13T00:09:00Z");

        assertThat(RecentBusinessTime.hoursAgo(clock, 2))
                .isBefore(RecentBusinessTime.hoursAgo(clock, 1));
    }

    /** ちょうど 00:00 は丸める先が無い。<strong>今日の 00:00 を返す。</strong> */
    @Test
    void ちょうど真夜中では今日の始まりを返す() {
        assertThat(RecentBusinessTime.hoursAgo(時計("2026-08-13T00:00:00Z"), 1))
                .isEqualTo(今日.atStartOfDay());
    }
}
