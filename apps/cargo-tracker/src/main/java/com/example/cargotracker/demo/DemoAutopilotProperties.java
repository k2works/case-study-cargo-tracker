package com.example.cargotracker.demo;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 自動実行の設定。
 *
 * <p><strong>既定は無効である。</strong> 有効化を明示した環境（local / dev）でのみ効くようにし、
 * 「本番でうっかり有効になる」経路を作らない（{@code app.demo-login} と同じ考え方）。
 *
 * @param enabled      有効か。<strong>ログイン画面のボタンもこの値で出し分ける</strong>
 * @param stepInterval 手順ごとの間隔。<strong>0 にすると一瞬で終わり、何が起きたか見えない</strong>
 * @param maxRuns      覚えておく実行の数。古いものから捨てる
 */
@ConfigurationProperties(prefix = "cargo-tracker.demo.autopilot")
public record DemoAutopilotProperties(
        boolean enabled, Duration stepInterval, int maxRuns) {

    public DemoAutopilotProperties {
        if (stepInterval == null || stepInterval.isNegative()) {
            stepInterval = Duration.ofMillis(1200);
        }
        if (maxRuns <= 0) {
            maxRuns = 20;
        }
    }
}
