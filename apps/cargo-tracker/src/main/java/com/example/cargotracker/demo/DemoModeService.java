package com.example.cargotracker.demo;

import jakarta.annotation.PreDestroy;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.random.RandomGenerator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

/**
 * デモモード —— <strong>入れている間ずっと、裏で業務が進み続ける</strong>。
 *
 * <p><strong>1 本のシナリオを見せるのではない。</strong> 複数の貨物が違う段階
 * （予約中・輸送中・通関中・請求済み）に同時に居て、数秒ごとにどれか 1 件が 1 手進む。
 * 利用者は<strong>普通に業務画面を開いているだけ</strong>で、一覧の件数や状態バッジが
 * 次々に変わっていくのを見る。
 *
 * <p><strong>1 件ずつ最後まで通さない理由。</strong> それだと一覧にはいつも同じ段階の
 * 貨物しか居らず、<strong>業務が並行して動いている様子にならない</strong>。
 *
 * <p><strong>進めるのは 1 つのスレッドだけである。</strong> 貨物の「どこまで進んだか」を
 * 複数のスレッドが書き換えると、同じ手順を二度実行したり飛ばしたりする。
 * 単一スレッドの定期実行にして、その中で順に触る。
 *
 * <p><strong>作ったものは印付きである</strong>（{@code DemoMark}）。契約番号の接頭辞を
 * 起点に {@code DemoModeCleanup} がまとめて片付けられる。
 */
@ConditionalOnProperty(name = "cargo-tracker.demo.install", havingValue = "true")
@Service
public class DemoModeService {

    private static final Logger LOG = LoggerFactory.getLogger(DemoModeService.class);

    private final DemoStepExecutor executor;
    private final DemoModeProperties properties;
    private final Clock clock;

    /** いま進めている貨物。<strong>触るのは実行スレッドだけである。</strong> */
    private final List<DemoCargoRun> active = new ArrayList<>();

    /** 帯に出す直近の出来事。<strong>書く側と読む側が別スレッドである。</strong> */
    private final Deque<DemoModeEvent> recent = new ConcurrentLinkedDeque<>();

    private final AtomicInteger completed = new AtomicInteger();
    private final AtomicInteger failed = new AtomicInteger();
    private final AtomicLong sequence = new AtomicLong(System.currentTimeMillis() % 100000);
    private final RandomGenerator random = RandomGenerator.getDefault();

    private final ScheduledExecutorService scheduler =
            Executors.newSingleThreadScheduledExecutor(runnable -> {
                Thread thread = new Thread(runnable, "demo-mode");
                // **アプリケーションの終了を妨げない。** 実行中に落としても待たせない
                thread.setDaemon(true);
                return thread;
            });

    /** 動いているときだけ非 null。<strong>止めるために持っている。</strong> */
    private volatile ScheduledFuture<?> ticking;

    DemoModeService(DemoStepExecutor executor, DemoModeProperties properties, Clock clock) {
        this.executor = executor;
        this.properties = properties;
        this.clock = clock;
    }

    /** デモモードが動いているか。 */
    public boolean running() {
        return ticking != null;
    }

    /**
     * デモモードを入れる。
     *
     * <p><strong>二度入れても増やさない。</strong> 押すたびに実行が重なると、
     * 間隔の設定が意味を持たなくなる。
     */
    synchronized void start() {
        if (running()) {
            return;
        }
        long interval = properties.stepInterval().toMillis();
        ticking = scheduler.scheduleWithFixedDelay(this::tick, 0, interval, TimeUnit.MILLISECONDS);
        record(null, "デモモードを開始しました", "");
        LOG.info("デモモードを開始しました（{} ごとに 1 手）", properties.stepInterval());
    }

    /**
     * デモモードを止める。
     *
     * <p><strong>作ったデータは消さない。</strong> 止めた時点の状態を見たいことがある。
     * 片付けは {@code DemoModeCleanup} が別に行う。
     */
    synchronized void stop() {
        ScheduledFuture<?> current = ticking;
        if (current == null) {
            return;
        }
        current.cancel(false);
        ticking = null;
        record(null, "デモモードを停止しました", "");
        LOG.info("デモモードを停止しました");
    }

    /** 画面に出す状況。 */
    DemoModeStatus status() {
        return new DemoModeStatus(
                running(),
                active.size(),
                completed.get(),
                failed.get(),
                properties.refreshInterval().toMillis(),
                List.copyOf(recent));
    }

    /** 履歴を空にする（片付けと合わせて呼ぶ）。 */
    synchronized void forgetAll() {
        active.clear();
        recent.clear();
        completed.set(0);
        failed.set(0);
    }

    /** 動いていれば止める（アプリケーションの終了時）。 */
    @PreDestroy
    void shutdown() {
        stop();
        scheduler.shutdownNow();
    }

    /**
     * 1 回分の仕事 —— <strong>足りなければ 1 件足し、どれか 1 件を 1 手進める</strong>。
     *
     * <p><strong>例外を外に漏らさない。</strong> 定期実行は例外が出ると
     * <strong>次回以降が黙って止まる</strong>。1 件の貨物が業務のルールで弾かれただけで
     * デモモード全体が動かなくなるのは、止まったことに気づけない分たちが悪い。
     */
    private void tick() {
        try {
            replenish();
            advanceOne();
        } catch (RuntimeException e) {
            LOG.warn("デモモードの 1 手で問題が起きました: {}", e.getMessage());
        }
    }

    /** 進めている貨物が足りなければ足す。 */
    private void replenish() {
        while (active.size() < properties.concurrentCargo()) {
            DemoScenario scenario = DemoScenario.random(random, sequence.incrementAndGet());
            active.add(new DemoCargoRun(scenario));
        }
    }

    /** どれか 1 件を 1 手だけ進める。 */
    private void advanceOne() {
        if (active.isEmpty()) {
            return;
        }
        DemoCargoRun cargo = active.get(random.nextInt(active.size()));
        DemoStep step = cargo.nextStep();
        try {
            executor.execute(cargo);
            record(cargo, step.title(), step.actor());
            cargo.advance();
            if (cargo.finished()) {
                completed.incrementAndGet();
                active.remove(cargo);
            }
        } catch (RuntimeException e) {
            String reason = e.getMessage() == null ? e.toString() : e.getMessage();
            cargo.fail(reason);
            failed.incrementAndGet();
            active.remove(cargo);
            record(cargo, "%s で止まりました".formatted(step.title()), step.actor());
            LOG.warn("デモモードの貨物が止まりました（{}）: {}", step, reason);
        }
    }

    /** 直近の出来事に足す。<strong>古いものから捨てる。</strong> */
    private void record(DemoCargoRun cargo, String what, String actor) {
        recent.addFirst(new DemoModeEvent(
                Instant.now(clock), what, actor,
                cargo == null ? null : cargo.trackingNumber(),
                cargo == null ? null : cargo.scenario().corporate().name()));
        while (recent.size() > properties.recentEvents()) {
            recent.removeLast();
        }
    }
}
