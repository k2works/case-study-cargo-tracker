package com.example.billingms.interfaces.scheduling;

import com.example.billingms.application.InvoiceQueryService;
import com.example.billingms.domain.commands.MarkOverdueCommand;
import com.example.billingms.domain.projections.InvoiceSummary;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import net.javacrumbs.shedlock.core.LockConfiguration;
import net.javacrumbs.shedlock.core.LockProvider;
import net.javacrumbs.shedlock.core.SimpleLock;
import org.axonframework.commandhandling.gateway.CommandGateway;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * {@link OverdueScheduler} の ShedLock 統合シミュレーションテスト
 * （IT8 T2.2 / ADR-0017 §受け入れテスト「2 instance 並列発火時に 1 instance のみが処理する」）。
 *
 * <p>実 DB を使わず、{@link LockProvider} 実装を共有することで「2 つの instance が
 * 同時に scheduledRun を呼ぶ → 1 つだけが lock を取得 → 残りは即座にスキップ」という
 * シナリオを再現する。lock の取得競合 + 1 instance 処理を SimpleMeterRegistry の counter で検証。</p>
 *
 * <p>本テストは @SchedulerLock アノテーション自体は使用せず、ShedLock の {@code LockProvider}
 * を直接呼んで「lock 取得 → scheduledRun の中身を実行 → lock 解放」のサイクルを 2 instance で
 * 並列実行する。これにより Spring AOP / @EnableSchedulerLock のロード（重い）を回避し、
 * ユニットテストとして高速に実行できる。実機での @SchedulerLock 動作は LocalH2SmokeTest +
 * 手動 Heroku web=2 デプロイで確認する（ADR-0017 §「受け入れテスト」）。</p>
 */
class OverdueSchedulerShedLockIntegrationTest {

    private InvoiceQueryService queryService;
    private CommandGateway commandGateway;
    private OverdueScheduler scheduler;

    @BeforeEach
    void setUp() {
        queryService = mock(InvoiceQueryService.class);
        commandGateway = mock(CommandGateway.class);
        scheduler = new OverdueScheduler(queryService, commandGateway, new SimpleMeterRegistry());
    }

    @Test
    @DisplayName("ADR-0017 T2.2: 2 instance 並列発火時に 1 instance のみが処理する（LockProvider 共有シミュレーション）")
    void twoInstanceParallel_onlyOneProcesses() throws Exception {
        // 共有 LockProvider（テスト中はメモリ内で lock 名を管理）
        InMemoryLockProvider sharedLockProvider = new InMemoryLockProvider();

        when(queryService.findOverdueCandidates()).thenReturn(List.of(
                summary("INV-001"), summary("INV-002"), summary("INV-003")));

        AtomicInteger instance1FiredCount = new AtomicInteger();
        AtomicInteger instance2FiredCount = new AtomicInteger();

        ExecutorService executor = Executors.newFixedThreadPool(2);

        executor.submit(() -> {
            Optional<SimpleLock> lock = sharedLockProvider.lock(
                    new LockConfiguration(java.time.Instant.now(),
                            "billing-overdue-scheduler",
                            Duration.ofHours(19),
                            Duration.ofHours(5)));
            if (lock.isPresent()) {
                instance1FiredCount.set(scheduler.runOverdueDetection());
                lock.get().unlock();
            }
        });

        executor.submit(() -> {
            Optional<SimpleLock> lock = sharedLockProvider.lock(
                    new LockConfiguration(java.time.Instant.now(),
                            "billing-overdue-scheduler",
                            Duration.ofHours(19),
                            Duration.ofHours(5)));
            if (lock.isPresent()) {
                instance2FiredCount.set(scheduler.runOverdueDetection());
                lock.get().unlock();
            }
        });

        executor.shutdown();
        executor.awaitTermination(10, TimeUnit.SECONDS);

        // どちらか一方のみが処理（3 件発火）、他方は lock 取れず 0 件
        int totalFired = instance1FiredCount.get() + instance2FiredCount.get();
        assertThat(totalFired)
                .as("2 instance 並列発火時、合計発火数は 1 instance 分（3 件）のみ")
                .isEqualTo(3);

        // commandGateway は 3 回のみ呼ばれている（重複なし）
        verify(commandGateway, times(3)).sendAndWait(any(MarkOverdueCommand.class));
    }

    @Test
    @DisplayName("ADR-0017 T2.2: lock 解放後の次回実行は別 instance が取得可能（lock 期限内であっても unlock すれば次サイクルで取得）")
    void afterUnlock_nextRunCanAcquireLock() {
        InMemoryLockProvider sharedLockProvider = new InMemoryLockProvider();

        when(queryService.findOverdueCandidates()).thenReturn(List.of(summary("INV-A")));

        // 1 回目: lock 取得 → 処理 → unlock
        Optional<SimpleLock> firstLock = sharedLockProvider.lock(
                new LockConfiguration(java.time.Instant.now(),
                        "billing-overdue-scheduler",
                        Duration.ofHours(19),
                        Duration.ofMillis(1)));
        assertThat(firstLock).isPresent();
        scheduler.runOverdueDetection();
        firstLock.get().unlock();

        // 2 回目: lock 取得可能（unlock 済み）
        Optional<SimpleLock> secondLock = sharedLockProvider.lock(
                new LockConfiguration(java.time.Instant.now(),
                        "billing-overdue-scheduler",
                        Duration.ofHours(19),
                        Duration.ofMillis(1)));
        assertThat(secondLock).isPresent();
        secondLock.get().unlock();
    }

    private InvoiceSummary summary(String invoiceId) {
        InvoiceSummary s = new InvoiceSummary();
        s.setInvoiceId(invoiceId);
        return s;
    }

    /**
     * インメモリ LockProvider（本テスト専用）。実機では {@code JdbcTemplateLockProvider} が
     * shedlock テーブルで同等の排他制御を提供する。
     */
    private static class InMemoryLockProvider implements LockProvider {
        private final ConcurrentHashMap<String, Object> locks = new ConcurrentHashMap<>();

        @Override
        public Optional<SimpleLock> lock(LockConfiguration lockConfiguration) {
            Object existing = locks.putIfAbsent(lockConfiguration.getName(), new Object());
            if (existing != null) {
                return Optional.empty();
            }
            return Optional.of(() -> locks.remove(lockConfiguration.getName()));
        }
    }
}
