package com.example.cargotracker.shared.infrastructure.time;

import java.time.Clock;
import java.time.ZoneId;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 業務の「今日」を決める時計。
 *
 * <p>業務タイムゾーンは Asia/Tokyo。UTC で判断すると、時差の分だけ「当日」の受付が
 * 拒否される時間帯ができる。日中しか動かさないと気づかない類の欠陥なので、
 * {@code Clock.systemUTC()} の直呼びは ArchUnit で禁止する。</p>
 */
@Configuration
public class BusinessClockConfiguration {

    public static final ZoneId BUSINESS_ZONE = ZoneId.of("Asia/Tokyo");

    @Bean
    public Clock clock() {
        return Clock.system(BUSINESS_ZONE);
    }
}
