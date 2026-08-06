package com.example.cargotracker.shared.infrastructure.config;

import java.time.Clock;
import java.time.ZoneId;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 業務日付を判断する時計。
 *
 * <p><strong>UTC ではなく業務のタイムゾーンで日付を判断する。</strong>
 * 「今日」「過去の日付」は利用者の暦の上の概念であり、サーバの標準時ではない。
 *
 * <p>時計を {@code Clock.systemUTC()} にしていたため、**日本時間の 0 時から 9 時のあいだ、
 * 当日着の予約が「到着期限に過去の日付は指定できません」で拒否されていた**
 * （UTC ではまだ前日であるため）。IT3 の作業中、日付をまたいだ時刻にテストが落ちて発覚した。
 * 日中しか動かさなければ一生気づかない類の欠陥である。
 *
 * <p>タイムゾーンは {@code app.business-zone} で変更できる。運用拠点が変わったときに
 * コードを触らずに済ませるためであり、**既定値に頼って設定を書かない運用を許さない**
 * わけではない（既定は日本の拠点）。
 */
@Configuration
public class BusinessClockConfiguration {

    private final ZoneId businessZone;

    public BusinessClockConfiguration(
            @Value("${app.business-zone:Asia/Tokyo}") String businessZone) {
        this.businessZone = ZoneId.of(businessZone);
    }

    /**
     * 業務日付の判断に使う時計。
     *
     * <p>永続化する日時は {@code TIMESTAMPTZ} であり時点そのものを保つ。本 Bean が
     * 影響するのは「その時点が業務上の何日か」の判断である。
     */
    @Bean
    public Clock clock() {
        return Clock.system(businessZone);
    }
}
