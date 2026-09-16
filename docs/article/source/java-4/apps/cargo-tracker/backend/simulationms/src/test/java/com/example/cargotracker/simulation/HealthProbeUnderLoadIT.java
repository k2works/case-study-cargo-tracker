package com.example.cargotracker.simulation;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.cargotracker.shared.testing.AbstractAxonIntegrationTest;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpStatus;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.TestPropertySource;
import org.springframework.web.client.RestClient;

/**
 * 実行中もヘルスチェックが劣化しない（US36 §受入基準 7）。
 *
 * <p><b>この局面に固有の危険 3 番（負荷をかける側が業務を止める）が、本 IT で
 * 初めて現実になる。</b> IT7 で「横断的な防御がヘルスチェックを殺し、過負荷で
 * 再起動ループに入った」同型を踏んでいるので、<b>同じ形を先に塞ぐ</b>。</p>
 *
 * <p><b>時間では判別しない。</b> 応答時間のアサートは混んだ機械で揺れる。
 * ここで見るのは「負荷をかけているあいだ、`/actuator/health` が
 * <b>1 度も 200 以外を返さない</b>」という<b>値</b>である。</p>
 *
 * <p><b>負荷は実行の糸を全部埋める形でかける。</b> 実行の糸は固定数（2 本）
 * なので、そこが詰まったときにヘルスチェックまで道連れになるかを見る
 * ——実際に危ないのは「共有の資源を待つ形で書いてしまう」ことである。</p>
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestPropertySource(properties = {
    "cargo-tracker.simulation.enabled=true",
    // **糸を塞ぐ検査なので、頃合いの糸は動かさない**（別の実行が割り込むと
    // 何が詰まらせたのか分からなくなる）。
    "cargo-tracker.simulation.schedule.enabled=false",
})
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class HealthProbeUnderLoadIT extends AbstractAxonIntegrationTest {

    /** 実行の糸の数より多く塞ぐ（余らせない）。 */
    private static final int LOAD_THREADS = 8;

    /** 負荷をかけている時間。<b>短くてよい</b>——見るのは値であって時間ではない。 */
    private static final Duration LOAD_WINDOW = Duration.ofSeconds(3);

    @LocalServerPort
    private int port;

    @org.springframework.beans.factory.annotation.Autowired
    private java.util.concurrent.Executor simulationExecutor;

    private final RestClient rest = RestClient.builder()
            .defaultStatusHandler(status -> true, (request, response) -> { })
            .build();

    @Test
    @DisplayName("US36 §7: 実行の糸が全部埋まっていても、ヘルスチェックは 200 を返し続ける")
    void healthStaysUpWhileEveryRunThreadIsBusy() throws InterruptedException {
        AtomicBoolean busy = new AtomicBoolean(true);
        // 実行の糸を全部埋める。**本物の実行と同じ場所を塞ぐ**——別の糸で
        // 眠らせても、確かめたい共有の資源に触らない。
        for (int i = 0; i < LOAD_THREADS; i++) {
            simulationExecutor.execute(() -> {
                while (busy.get()) {
                    try {
                        TimeUnit.MILLISECONDS.sleep(50);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        return;
                    }
                }
            });
        }

        AtomicInteger probes = new AtomicInteger();
        List<Integer> statuses = new java.util.concurrent.CopyOnWriteArrayList<>();
        ExecutorService prober = Executors.newSingleThreadExecutor();
        try {
            Instant deadline = Instant.now().plus(LOAD_WINDOW);
            prober.execute(() -> {
                while (Instant.now().isBefore(deadline)) {
                    statuses.add(rest.get()
                            .uri("http://localhost:" + port + "/actuator/health")
                            .retrieve().toBodilessEntity().getStatusCode().value());
                    probes.incrementAndGet();
                }
            });
            prober.shutdown();
            assertThat(prober.awaitTermination(60, TimeUnit.SECONDS)).isTrue();
        } finally {
            busy.set(false);
        }

        assertThat(probes.get())
                .as("**検査が空振りしていない**——1 度も叩いていなければ何も確かめていない")
                .isGreaterThan(5);
        assertThat(statuses)
                .as("負荷をかけているあいだの応答: %s", statuses)
                .containsOnly(HttpStatus.OK.value());
    }
}
