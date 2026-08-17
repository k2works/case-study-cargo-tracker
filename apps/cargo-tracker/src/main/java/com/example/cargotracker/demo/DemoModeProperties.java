package com.example.cargotracker.demo;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * デモモードの設定。
 *
 * <p><strong>既定は無効である。</strong> 開始の入口は認証の外（{@code /public/**}）にあり、
 * 有効なら誰でも荷主と予約を作れる。有効化は local / dev にだけ書く
 * （{@code app.demo-login} と同じ考え方）。
 *
 * @param enabled         使えるか。<strong>ログイン画面のボタンもこの値で出し分ける</strong>
 * @param stepInterval    1 手進めるごとの間隔。<strong>短すぎると画面を読む前に変わる</strong>
 * @param concurrentCargo 同時に進める貨物の数。
 *                        <strong>1 だと一覧に段階の違う貨物が並ばない</strong>
 * @param refreshInterval 業務画面を再読み込みする間隔。
 *                        <strong>{@code stepInterval} より短くしても意味がない</strong>
 * @param recentEvents    帯に出す直近の出来事の数
 */
@ConfigurationProperties(prefix = "cargo-tracker.demo.mode")
public record DemoModeProperties(
        boolean enabled,
        Duration stepInterval,
        int concurrentCargo,
        Duration refreshInterval,
        int recentEvents) {

    public DemoModeProperties {
        if (stepInterval == null || stepInterval.isNegative() || stepInterval.isZero()) {
            stepInterval = Duration.ofMillis(2500);
        }
        if (concurrentCargo <= 0) {
            concurrentCargo = 4;
        }
        if (refreshInterval == null || refreshInterval.isNegative() || refreshInterval.isZero()) {
            refreshInterval = Duration.ofSeconds(5);
        }
        if (recentEvents <= 0) {
            recentEvents = 8;
        }
    }
}
