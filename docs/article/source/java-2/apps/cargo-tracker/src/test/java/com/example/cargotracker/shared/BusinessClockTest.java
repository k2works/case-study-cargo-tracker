package com.example.cargotracker.shared;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.cargotracker.shared.infrastructure.config.BusinessClockConfiguration;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;

/**
 * 業務日付を業務のタイムゾーンで判断していることを検証する。
 *
 * <p><strong>時計を UTC にしていたため、日本時間の 0 時から 9 時のあいだ、
 * 当日着の予約が拒否されていた。</strong> 日中しか動かさなければ一生気づかない。
 * IT3 の作業中、たまたま日付をまたいだ時刻にテストが落ちて発覚した。
 *
 * <p>**現在時刻に依存させない。** 固定の時点を与えて、業務日付がどう決まるかだけを見る。
 *
 * <p>予約が実際に受け付けられるかは Booking Context の関心事であり、
 * {@code CargoTest} が検証する（BC をまたいで参照しない。ArchUnit ルール 4）。
 */
class BusinessClockTest {

    /** 2026-08-06 15:11 UTC = 2026-08-07 00:11 JST。日付が食い違う時間帯。 */
    private static final Instant 深夜のJST = Instant.parse("2026-08-06T15:11:00Z");

    @Test
    void 業務日付は日本時間で決まる() {
        Clock clock = Clock.fixed(深夜のJST, ZoneId.of("Asia/Tokyo"));

        assertThat(LocalDate.now(clock)).isEqualTo(LocalDate.of(2026, java.time.Month.AUGUST, 7));
    }

    @Test
    void 協定世界時の時計では業務日付が前日になる() {
        Clock utc = Clock.fixed(深夜のJST, ZoneOffset.UTC);

        assertThat(LocalDate.now(utc))
                .as("この差がそのまま「当日着の予約が拒否される」という不具合になる")
                .isEqualTo(LocalDate.of(2026, java.time.Month.AUGUST, 6));
    }

    /** 設定した拠点のタイムゾーンで時計が作られる。 */
    @Test
    void 設定したタイムゾーンの時計を作る() {
        Clock clock = new BusinessClockConfiguration("Asia/Tokyo").clock();

        assertThat(clock.getZone()).isEqualTo(ZoneId.of("Asia/Tokyo"));
    }

    /** 拠点が変わったときにコードを触らずに済むこと。 */
    @Test
    void 拠点のタイムゾーンは設定で変えられる() {
        Clock clock = new BusinessClockConfiguration("America/Los_Angeles").clock();

        assertThat(clock.getZone()).isEqualTo(ZoneId.of("America/Los_Angeles"));
    }
}
