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
 * @param enabled           有効にするか
 * @param requestsPerWindow 1 つの送信元が窓の中で送れる回数
 * @param window            数える窓の長さ
 */
@ConfigurationProperties(prefix = "cargotracker.public-rate-limit")
public record PublicRateLimitProperties(
        boolean enabled,
        int requestsPerWindow,
        Duration window) {

    public PublicRateLimitProperties {
        if (requestsPerWindow <= 0) {
            requestsPerWindow = 100;
        }
        if (window == null || window.isZero() || window.isNegative()) {
            window = Duration.ofSeconds(1);
        }
    }
}
