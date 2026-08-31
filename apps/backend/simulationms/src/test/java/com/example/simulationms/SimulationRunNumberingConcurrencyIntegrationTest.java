package com.example.simulationms;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.simulationms.application.internal.commandservices.RunSimulationUseCase;
import com.example.simulationms.application.internal.outboundservices.acl.BusinessGateway;
import com.example.simulationms.domain.model.valueobjects.Scenario;
import com.example.simulationms.domain.model.valueobjects.ScenarioStep;
import com.example.simulationms.domain.repository.SimulationRunRepository;
import java.time.Clock;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * 実行 ID の採番が、同時開始でも衝突しないこと（IT15 Phase 0.4）。
 *
 * <p><strong>US37 の前提である。</strong>継続実行は同時に複数の実行を始めるため、
 * 採番が衝突すると継続実行そのものが始められない。IT14 の実行は管理者が 1 件ずつ
 * 押す形だったので表面化しなかった——<strong>作成しか起きないうちは成立し、
 * 最初の同時開始で壊れる</strong>形である。
 *
 * <p><strong>実 DB で確かめる。</strong>衝突は一意制約の上で起きるので、
 * スタブのリポジトリでは何も起きない。
 */
@SpringBootTest
@Testcontainers
@ExtendWith(SpringExtension.class)
@ActiveProfiles("integration")
@DisplayName("実行 ID の採番（同時開始）")
class SimulationRunNumberingConcurrencyIntegrationTest {

    private static final int CONCURRENCY = 8;

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired
    private SimulationRunRepository runs;

    /** 工程は業務を呼ばない。見たいのは採番だけである。 */
    private static final BusinessGateway NO_BUSINESS = (step, context) -> null;

    /**
     * シナリオごとに ID を分ける。同じシナリオだと二重実行の拒否が先に働き、
     * <strong>採番の衝突まで到達しない</strong>。
     */
    private static Scenario scenario(int index) {
        return Scenario.of("numbering-" + index, List.of(ScenarioStep.REGISTER_SHIPPER));
    }

    @Test
    @DisplayName("同時に開始しても、実行 ID は 1 つずつ違う番号になる")
    void assignsDistinctRunIdsWhenStartedConcurrently() throws Exception {
        RunSimulationUseCase useCase =
                new RunSimulationUseCase(runs, NO_BUSINESS, Clock.systemUTC());
        CyclicBarrier startTogether = new CyclicBarrier(CONCURRENCY);

        List<String> assigned;
        try (ExecutorService pool = Executors.newFixedThreadPool(CONCURRENCY)) {
            List<Callable<String>> starts = java.util.stream.IntStream.range(0, CONCURRENCY)
                    .<Callable<String>>mapToObj(i -> () -> {
                        startTogether.await();
                        return useCase.run(scenario(i), "admin01").runId().value();
                    })
                    .toList();
            List<Future<String>> futures = pool.invokeAll(starts);
            assigned = futures.stream().map(SimulationRunNumberingConcurrencyIntegrationTest::get)
                    .toList();
        }

        assertThat(assigned).doesNotContainNull().hasSize(CONCURRENCY);
        assertThat(assigned).doesNotHaveDuplicates();
    }

    private static String get(Future<String> future) {
        try {
            return future.get();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        } catch (java.util.concurrent.ExecutionException e) {
            throw new IllegalStateException("同時開始が失敗しました", e.getCause());
        }
    }
}
