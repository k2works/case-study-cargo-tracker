package com.example.cargotracker.simulation.application;

import com.example.cargotracker.shared.domain.error.BusinessRuleViolation;
import com.example.cargotracker.shared.domain.error.IllegalTransition;
import org.springframework.dao.DuplicateKeyException;
import com.example.cargotracker.simulation.domain.model.aggregates.SimulationRun;
import com.example.cargotracker.simulation.domain.model.valueobjects.Scenario;
import com.example.cargotracker.simulation.domain.model.valueobjects.ScenarioInput;
import com.example.cargotracker.simulation.infrastructure.config.SimulationProperties;
import com.example.cargotracker.simulation.infrastructure.persistence.SimulationRunMapper;
import java.time.Clock;
import java.util.UUID;
import java.util.concurrent.Executor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * シナリオの実行を受け付けて走らせる（US33・US34 / [ADR-0020]）。
 *
 * <p><b>応答を待たせない。</b> 標準シナリオは 13 工程を連鎖の追いつきを待ちながら
 * 進むので、終わるまで返さない形にすると要求が先に切れる。始めたことだけを返し、
 * 途中経過は読み口（S93）が出す。</p>
 */
public class SimulationService {

    private static final Logger log = LoggerFactory.getLogger(SimulationService.class);

    private final SimulationRunMapper runs;
    private final SimulationProperties properties;
    private final RunnerFactory runnerFactory;
    private final Executor executor;
    private final Clock clock;

    public SimulationService(SimulationRunMapper runs, SimulationProperties properties,
            RunnerFactory runnerFactory, Executor executor, Clock clock) {
        this.runs = runs;
        this.properties = properties;
        this.runnerFactory = runnerFactory;
        this.executor = executor;
        this.clock = clock;
    }

    /**
     * 実行ごとの走らせ手を作る。
     *
     * <p><b>使い回さない。</b> 作った識別子もトークンも実行の中でしか意味を持たない
     * （[ADR-0020] 決定 2）。</p>
     */
    @FunctionalInterface
    public interface RunnerFactory {

        /** その条件でシナリオを走らせる手を作る。 */
        SimulationRunner create(ScenarioInput input, SimulationRunner.StepListener listener);
    }

    /**
     * 実行を始める。
     *
     * <p><b>本番では断る</b>（US33 §受入基準 4）。実データに紛れる貨物を作らない。</p>
     *
     * <p><b>二重実行も断る</b>（§受入基準 5）。断りに<b>実行中の識別子を添える</b>
     * ——「二重に実行できません」だけでは、いまの結果へ行けない。</p>
     *
     * @return 始めた実行の識別子。<b>画面はこれで S93 へ移る</b>
     */
    public String start(Scenario scenario, String startedBy) {
        // 手で流すときは既定の条件で流す（US33）。継続実行は乱数が選ぶ（US36）。
        return start(ScenarioInput.standard(scenario), null, startedBy);
    }

    /**
     * 条件を指定して実行を始める（US36 §受入基準 1）。
     *
     * @param scheduleId どの稼働が流したか。<b>手で流した実行では {@code null}</b>
     */
    public String start(ScenarioInput input, String scheduleId, String startedBy) {
        Scenario scenario = input.scenario();
        if (!properties.enabled()) {
            throw new BusinessRuleViolation(
                    "この環境では業務シミュレーションを実行できません"
                            + "（実データに紛れる貨物を作らないため）");
        }
        var running = runs.findRunning(scenario.name());
        if (running != null) {
            throw new IllegalTransition("シナリオ「" + scenario.label()
                    + "」は実行中です（実行 " + running.runId() + "）。その結果を開いてください");
        }
        // **36 文字に収める。** 列は VARCHAR(36) で、接頭辞 + UUID をそのまま
        // 繋ぐと 40 文字になってあふれる（billingms の `PAY-` と同じ形。3 度目）。
        String runId = "SIM-" + UUID.randomUUID().toString().replace("-", "");
        SimulationRun run = SimulationRun.start(runId, scenario, null, startedBy,
                clock.instant());
        // **記録してから走らせる。** 先に走らせると、最初の工程が終わるまで
        // 実行が読み口に現れず、画面が「始まっていない」と読む。
        try {
            runs.insert(new SimulationRunMapper.RunRow(runId, scenario.name(),
                    run.status().name(), null, run.startedAt(), null, run.startedBy(),
                    clock.instant(), scheduleId));
        } catch (DuplicateKeyException e) {
            // **読んでから書くまでの隙間で、もう 1 本が始まった。** 上の
            // `findRunning` は同時押しを防げない——守りは部分ユニーク索引の側に
            // あり、ここへは制約違反として届く。**500 にしない**（IT16 のレビュー
            // N9）。利用者から見れば「実行中です」と同じ出来事である。
            var winner = runs.findRunning(scenario.name());
            throw new IllegalTransition("シナリオ「" + scenario.label()
                    + "」は実行中です"
                    + (winner == null ? "" : "（実行 " + winner.runId() + "）")
                    + "。その結果を開いてください", e);
        }
        executor.execute(() -> execute(run, input));
        return runId;
    }

    private void execute(SimulationRun run, ScenarioInput input) {
        try {
            runnerFactory.create(input, this::writeStep).run(run);
        } catch (RuntimeException e) {
            log.error("実行が例外で終わった: runId={}", run.runId(), e);
        } finally {
            // **決着させる。** 実行中のまま残ると、二重実行の守りがその実行を
            // 掴んだまま離さず、そのシナリオは二度と流せなくなる。
            // **finally に置く。** catch に置くと、例外にならない抜け方
            //（Error・中断）で実行中のまま残る。終わった実行は書き換えない。
            run.abort(clock.instant());
            runs.updateStatus(run.runId(), run.status().name(), run.finishedAt(),
                    clock.instant());
        }
    }

    private void writeStep(SimulationRun run, SimulationRun.RecordedStep step) {
        runs.insertStep(new SimulationRunMapper.StepRow(run.runId(), step.stepNo(),
                step.kind().name(), step.outcome().name(), step.elapsed().toMillis(),
                step.producedId(), step.failureStatus(), step.failureMessage(),
                step.occurredAt(), step.waited().toMillis()));
    }
}
