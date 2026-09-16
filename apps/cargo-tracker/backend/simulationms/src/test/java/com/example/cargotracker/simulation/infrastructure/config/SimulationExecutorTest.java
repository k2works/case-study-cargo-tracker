package com.example.cargotracker.simulation.infrastructure.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.cargotracker.simulation.domain.model.valueobjects.Scenario;
import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 選べる数だけ走れる（S92 の「まとめて流す」）。
 *
 * <p><b>実行は記録してから走らせる。</b> 糸が足りないと、記録だけ「実行中」で
 * 置かれたまま順番待ちになり、画面には<b>進まない実行</b>が並ぶ——止まって
 * いるのか混んでいるのかを、見ている人は区別できない。</p>
 *
 * <p><b>数を数え上げて確かめる。</b> 「2 本で足りる」と書き写すと、シナリオを
 * 足したときに片方だけが直る。</p>
 */
class SimulationExecutorTest {

    @Test
    @DisplayName("シナリオの数だけ同時に走れる（順番待ちで「実行中なのに進まない」を作らない）")
    void runsAsManyScenariosAsCanBeChosen() throws InterruptedException {
        int scenarios = Scenario.values().length;
        ExecutorService executor = new SimulationConfig().simulationExecutor();
        try {
            // **全部が同時に走らないと、この掛け金は開かない。** 糸が 1 本でも
            // 足りなければ、最後の 1 つは先の 1 つが終わるまで始まらない。
            CountDownLatch started = new CountDownLatch(scenarios);
            CountDownLatch release = new CountDownLatch(1);
            for (int i = 0; i < scenarios; i++) {
                executor.execute(() -> {
                    started.countDown();
                    try {
                        release.await();
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                });
            }

            assertThat(started.await(5, TimeUnit.SECONDS))
                    .as("シナリオ %d 本を同時に始められない（順番待ちが出る）", scenarios)
                    .isTrue();
            release.countDown();
        } finally {
            executor.shutdownNow();
            assertThat(executor.awaitTermination(Duration.ofSeconds(5).toSeconds(),
                    TimeUnit.SECONDS)).isTrue();
        }
    }
}
