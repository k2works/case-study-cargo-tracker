package com.example.simulationms;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.simulationms.application.internal.commandservices.ContinuousRunner;
import com.example.simulationms.application.internal.commandservices.RunSimulationUseCase;
import com.example.simulationms.domain.model.valueobjects.ContinuousRunPolicy;
import com.example.simulationms.domain.model.valueobjects.ScenarioRequest;
import com.example.simulationms.domain.model.valueobjects.Seed;
import com.example.simulationms.domain.model.valueobjects.SessionId;
import com.example.simulationms.infrastructure.scheduling.AsyncContinuousRunner;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

/**
 * 継続実行の上限が、ヘルスチェックを巻き込まないこと（US37-7・[ADR-031] 決定 3）。
 *
 * <p><strong>IT7 の再発防止である。</strong>横断的な防御を一律に適用した結果、
 * 過負荷のとき liveness が 503 を返して Pod が再起動ループに入った。
 * 負荷をかける側を自分で作るなら、同じ形を先に塞いでおく。
 *
 * <p>ここで確かめるのは 2 つ——<strong>実行を走らせるスレッドが埋まっても</strong>
 * ヘルスチェックが通ること、そして<strong>上限に達したら新しい実行を始めない</strong>こと。
 * 片方だけだと、上限を外した実装でも緑になる。
 */
@SpringBootTest
@AutoConfigureMockMvc
@DisplayName("継続実行とヘルスチェック")
class SimulationHealthNotThrottledTest {

    @Autowired
    private MockMvc mockMvc;

    /**
     * <strong>実行のスレッドを埋めた状態で</strong>ヘルスチェックが通る。
     *
     * <p>実行のプールを Web の要求と同じところで捌く実装に戻すと、ここが詰まる。
     */
    @Test
    @DisplayName("実行のスレッドが埋まっていても、ヘルスチェックは通る")
    void healthStaysUpWhileRunsOccupyEveryThread() throws Exception {
        int threads = ContinuousRunPolicy.MAX_CONCURRENT_LIMIT;
        CountDownLatch occupied = new CountDownLatch(threads);
        CountDownLatch release = new CountDownLatch(1);

        try (ExecutorService pool = Executors.newFixedThreadPool(threads)) {
            for (int i = 0; i < threads; i++) {
                pool.execute(() -> {
                    occupied.countDown();
                    try {
                        release.await(30, TimeUnit.SECONDS);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                });
            }
            assertThat(occupied.await(10, TimeUnit.SECONDS))
                    .as("実行のスレッドを埋められていない場合、この検査は何も守らない")
                    .isTrue();

            // **ヘルスチェックは認証も要らない**（[ADR-007] の除外）。
            // 上限に掛かる経路とは別のところを通る
            mockMvc.perform(get("/actuator/health/liveness")).andExpect(status().isOk());
            mockMvc.perform(get("/actuator/health/readiness")).andExpect(status().isOk());

            release.countDown();
        }
    }

    /**
     * <strong>上限に達したら、新しい実行は始まらない。</strong>
     *
     * <p>ここが無いと、上のテストは「上限が無い実装」でも緑になる。
     */
    @Test
    @DisplayName("走っている数は、始めた分だけ増える")
    void countsWhatIsActuallyRunning() throws Exception {
        CountDownLatch started = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        RunSimulationUseCase blocking = new BlockingUseCase(started, release);

        try (ExecutorService pool = Executors.newFixedThreadPool(2)) {
            ContinuousRunner runner = new AsyncContinuousRunner(blocking, pool);
            assertThat(runner.running()).isZero();

            runner.start(request(), SessionId.of("SES-20261207-0001"), Seed.of(1L));
            assertThat(started.await(10, TimeUnit.SECONDS)).isTrue();
            assertThat(runner.running()).isEqualTo(1);

            release.countDown();
        }
    }

    private static ScenarioRequest request() {
        return new ScenarioRequest(
                com.example.simulationms.domain.model.valueobjects.Scenario.standardTransport(),
                "JPTYO", "USLAX", "GENERAL", 900, 120);
    }

    /** 走り続ける実行。上限の判定が「走っている数」を見ていることを確かめるために使う。 */
    private static final class BlockingUseCase extends RunSimulationUseCase {

        private final CountDownLatch started;
        private final CountDownLatch release;

        BlockingUseCase(CountDownLatch started, CountDownLatch release) {
            super(null, null, java.time.Clock.systemUTC());
            this.started = started;
            this.release = release;
        }

        @Override
        public com.example.simulationms.domain.model.aggregates.SimulationRun runForSession(
                ScenarioRequest request, SessionId sessionId, Seed seed) {
            started.countDown();
            try {
                release.await(30, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            return null;
        }
    }
}
