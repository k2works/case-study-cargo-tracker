package com.example.cargotracker.shared.infrastructure.web;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 公開エンドポイントのレートリミット設定（{@code non_functional.md} §2.2）。
 *
 * <p><strong>外部化するのは、テストで到達できる上限にするためである。</strong>
 * コメントで「テストでは小さく」と書くだけでは小さくならない
 * （IT6 の追跡ポーリング間隔と同じ扱い）。
 *
 * @param enabled            有効にするか
 * @param requestsPerWindow  1 つの送信元が窓の中で送れる回数
 * @param window             数える窓の長さ
 * @param trustedProxyCount  自分の前に居る<strong>信頼できる</strong>プロキシの段数（ADR-011）。
 *                           <strong>既定は 0 = ヘッダを信用しない。</strong>
 *                           ALB 1 段なら 1 を設定する。<strong>環境ごとの事実であり、
 *                           コードに書けない</strong>ため設定で持つ
 */
@ConfigurationProperties(prefix = "cargotracker.public-rate-limit")
public record PublicRateLimitProperties(
        boolean enabled,
        int requestsPerWindow,
        Duration window,
        int trustedProxyCount) {

    public PublicRateLimitProperties {
        // **安全側を既定にする。** 設定を忘れた環境でヘッダを信用すると、
        // ヘッダを変えるだけで制限を回避できる状態になる
        if (trustedProxyCount < 0) {
            trustedProxyCount = 0;
        }
        if (requestsPerWindow <= 0) {
            requestsPerWindow = 100;
        }
        if (window == null || window.isZero() || window.isNegative()) {
            window = Duration.ofSeconds(1);
        }
    }
}
