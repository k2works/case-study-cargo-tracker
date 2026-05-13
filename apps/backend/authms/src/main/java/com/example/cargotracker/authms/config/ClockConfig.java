package com.example.cargotracker.authms.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

/**
 * 時刻関連の Bean 定義。
 *
 * <p>{@link Clock} を Bean として公開することで、テスト時に {@code Clock.fixed()} で
 * 上書き可能になる（US00-r1 アカウントロックの時刻管理用）。</p>
 */
@Configuration
public class ClockConfig {

    @Bean
    public Clock clock() {
        return Clock.systemDefaultZone();
    }
}
