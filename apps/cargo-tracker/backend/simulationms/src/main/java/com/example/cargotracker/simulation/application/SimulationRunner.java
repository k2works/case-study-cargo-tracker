package com.example.cargotracker.simulation.application;

import com.example.cargotracker.simulation.domain.model.aggregates.SimulationRun;
import com.example.cargotracker.simulation.domain.model.valueobjects.StepKind;
import java.time.Clock;
import java.time.Duration;
import java.util.EnumMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 工程を順に実行する（US33 §受入基準 1・6 / US34 §受入基準 1・3）。
 *
 * <p><b>止まったら、そこで終える。</b> 以降の工程を実行しないのは、失敗したあとに
 * 業務データを増やさないためである。<b>それまでに作られたものは取り消さない</b>
 * ——どこまで進んだかを追えることが US34 の目的である。</p>
 */
public class SimulationRunner {

    private static final Logger log = LoggerFactory.getLogger(SimulationRunner.class);

    private final BusinessApi api;
    private final Clock clock;

    public SimulationRunner(BusinessApi api, Clock clock) {
        this.api = api;
        this.clock = clock;
    }

    /**
     * シナリオの工程を順に実行する。
     *
     * <p><b>投げっぱなしにしない。</b> 予期しない例外（接続できない等）も
     * 失敗した工程として記録する——実行が決着しないと、二重実行の守り
     * （実行中は 1 本だけ）がその実行を掴んだまま離さなくなる。</p>
     */
    public void run(SimulationRun run) {
        Map<StepKind, String> produced = new EnumMap<>(StepKind.class);

        for (StepKind kind : run.plannedSteps()) {
            long startedAtNanos = System.nanoTime();
            BusinessApi.StepResult result;
            try {
                result = api.execute(kind, Map.copyOf(produced));
            } catch (RuntimeException e) {
                log.warn("工程が例外で終わった: runId={} step={}", run.runId(), kind, e);
                result = BusinessApi.StepResult.failure(500, e.getMessage());
            }
            Duration elapsed = Duration.ofNanos(System.nanoTime() - startedAtNanos);

            if (!result.succeeded()) {
                run.recordFailure(kind, elapsed, result.failureStatus(),
                        result.failureMessage(), clock.instant());
                // **以降は実行しない。** 止まったあとに業務データを増やさない。
                return;
            }
            run.recordSuccess(kind, elapsed, result.producedId(), clock.instant());
            if (result.producedId() != null) {
                produced.put(kind, result.producedId());
            }
        }
    }
}
