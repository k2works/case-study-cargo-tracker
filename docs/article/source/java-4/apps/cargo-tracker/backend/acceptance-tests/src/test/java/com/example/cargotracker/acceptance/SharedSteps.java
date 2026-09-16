package com.example.cargotracker.acceptance;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import java.time.Duration;
import java.util.concurrent.Callable;

/**
 * 「N 秒以内に」を 1 か所に閉じる。
 *
 * <p>各ステップで sleep を書くと、待ち時間が散らばって全体が遅くなり、しかも
 * 「たまたま間に合った」テストになる。Awaitility に寄せて条件で待つ。</p>
 */
final class SharedSteps {

    private SharedSteps() {
    }

    static void awaitWithin(int seconds, Callable<Boolean> condition, String description) {
        try {
            await(description)
                    .atMost(Duration.ofSeconds(seconds))
                    .pollInterval(Duration.ofMillis(300))
                    .until(condition);
        } catch (org.awaitility.core.ConditionTimeoutException e) {
            assertThat(false).as("%s（%d 秒以内に起きなかった）", description, seconds).isTrue();
        }
    }
}
