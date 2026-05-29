package com.example.trackingms.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

/**
 * trackingms 全体で共有する基盤 Bean を定義する設定クラス。
 *
 * <p>現状は {@link Clock} のみを公開する。{@code Clock} はドメインサービスや集約から
 * 時刻取得を抽象化するために注入される（テスト時には固定 {@code Clock} に差し替え可能）。</p>
 */
@Configuration
public class TrackingCommonConfig {

    /**
     * システム時刻を返す既定の {@link Clock}。テストでは固定 Clock に差し替えて時刻依存テストを安定化させる。
     */
    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }
}
