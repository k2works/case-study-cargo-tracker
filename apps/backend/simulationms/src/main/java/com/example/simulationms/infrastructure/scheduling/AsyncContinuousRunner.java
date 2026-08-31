package com.example.simulationms.infrastructure.scheduling;

import com.example.simulationms.application.internal.commandservices.ContinuousRunner;
import com.example.simulationms.application.internal.commandservices.RunSimulationUseCase;
import com.example.simulationms.domain.model.valueobjects.ScenarioRequest;
import com.example.simulationms.domain.model.valueobjects.Seed;
import com.example.simulationms.domain.model.valueobjects.SessionId;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicInteger;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 継続実行の 1 件を、別のスレッドで走らせる（US37・[ADR-031] 決定 3）。
 *
 * <p><strong>走っている数を数えるのはここである。</strong>上限の判定はこの数を見る。
 *
 * <p><strong>ヘルスチェックはこの数の外にいる。</strong>上限に達しても liveness /
 * readiness は同じ経路を通らない——一律に掛けると、過負荷のとき Pod が再起動して
 * 自分で自分を止める（IT7 で実際に起きた）。
 */
public class AsyncContinuousRunner implements ContinuousRunner {

    private static final Logger log = LoggerFactory.getLogger(AsyncContinuousRunner.class);

    private final RunSimulationUseCase runSimulation;
    private final Executor executor;

    /** いま走っている数。上限の判定に使う。 */
    private final AtomicInteger inFlight = new AtomicInteger();

    public AsyncContinuousRunner(RunSimulationUseCase runSimulation, Executor executor) {
        this.runSimulation = runSimulation;
        this.executor = executor;
    }

    @Override
    public void start(ScenarioRequest request, SessionId sessionId, Seed seed) {
        inFlight.incrementAndGet();
        executor.execute(() -> {
            try {
                runSimulation.runForSession(request, sessionId, seed);
            } catch (RuntimeException e) {
                // **握りつぶさない。**継続実行は誰も見ていない時間に動くため、
                // ここで消すと「動いているのに何も起きない」状態になる。
                // 工程の失敗そのものは実行の記録に残る（[ADR-030] 決定 5）
                log.warn("継続実行の 1 件が想定外の失敗で終わりました: session={} scenario={}",
                        sessionId.value(), request.scenario().id(), e);
            } finally {
                inFlight.decrementAndGet();
            }
        });
    }

    @Override
    public int running() {
        return inFlight.get();
    }
}
