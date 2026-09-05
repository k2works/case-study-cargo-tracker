package com.example.cargotracker.acceptance.routing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import java.time.Duration;
import java.util.concurrent.Callable;

/** 「N 秒以内に」を 1 か所に閉じる（bookingms 側の SharedSteps と同じ形）。 */
final class SharedRoutingSteps {

    private SharedRoutingSteps() {
    }

    static void awaitWithin(int seconds, Callable<Boolean> condition, String description) {
        awaitWithin(seconds, condition, description, true);
    }

    /**
     * 条件が起きるのを待つ。
     *
     * <p>{@code expected} を false にすると「その時間のあいだ起きないこと」を
     * 確かめる。<b>「変わらない」は待たなければ判別できない。</b> 投影は非同期なので、
     * 直後に読んだ古い値は「変わっていない」と区別が付かない。</p>
     */
    static void awaitWithin(int seconds, Callable<Boolean> condition, String description,
            boolean expected) {
        if (!expected) {
            try {
                await(description)
                        .atMost(Duration.ofSeconds(seconds))
                        .pollInterval(Duration.ofMillis(300))
                        .until(condition);
            } catch (org.awaitility.core.ConditionTimeoutException e) {
                return;
            }
            assertThat(false).as("%s（起きてはいけないことが起きた）", description).isTrue();
            return;
        }
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
