package com.example.simulationms.application.internal.commandservices;

import com.example.simulationms.application.internal.outboundservices.acl.BusinessCallFailedException;
import com.example.simulationms.application.internal.outboundservices.acl.BusinessGateway;
import com.example.simulationms.domain.model.aggregates.SimulationRun;
import com.example.simulationms.domain.model.valueobjects.BusinessContextKey;
import com.example.simulationms.domain.model.valueobjects.RunId;
import com.example.simulationms.domain.model.valueobjects.Scenario;
import com.example.simulationms.domain.model.valueobjects.ScenarioStep;
import com.example.simulationms.domain.model.valueobjects.StepResult;
import com.example.simulationms.domain.repository.SimulationRunRepository;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;

/**
 * シナリオを 1 本流す（US34）。
 *
 * <p><strong>{@code @Transactional} を置かない</strong>（[ADR-030] 決定 5）。工程ごとに
 * 独立して記録し、失敗しても巻き戻さない。まとめて 1 つのトランザクションにすると、
 * 失敗したときに<strong>どこまで進んだかの記録ごと消える</strong>——US35 が見たいものが
 * 失敗したときだけ残らない。IT12 で入金の記録が巻き戻された形の裏返しである。
 */
public class RunSimulationUseCase {

    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("yyyyMMdd");

    /**
     * これより長く音沙汰の無い実行は、実行中とみなさない。
     *
     * <p>Pod の再起動や配備で途中終了した行を実行中のまま残すと、そのシナリオは
     * <strong>二度と実行できなくなる</strong>。1 本の実行は工程ごとに期限（読み取り 10 秒）を
     * 持つため、全 14 工程が詰まっても 3 分に届かない。余裕を見て 15 分とする。
     */
    private static final Duration STALE_AFTER = Duration.ofMinutes(15);

    private final SimulationRunRepository runs;
    private final BusinessGateway business;
    private final Clock clock;

    public RunSimulationUseCase(SimulationRunRepository runs, BusinessGateway business,
            Clock clock) {
        this.runs = runs;
        this.business = business;
        this.clock = clock;
    }

    public SimulationRun run(Scenario scenario, String startedBy) {
        runs.findRunningByScenario(scenario, clock.instant().minus(STALE_AFTER))
                .ifPresent(running -> {
                    throw new SimulationAlreadyRunningException(running.runId());
                });

        RunId runId = nextRunId();
        SimulationRun run = SimulationRun.start(runId, scenario, startedBy, clock.instant());
        runs.create(run);

        Map<String, String> context = new HashMap<>();
        context.put(BusinessContextKey.RUN_ID, runId.value());

        for (ScenarioStep step : scenario.steps()) {
            StepResult result = execute(step, context);
            run = run.withResult(result);
            runs.appendResult(runId, result);

            if (result.failed()) {
                // **後ろの工程は踏まない。**前提の無い操作が次々に失敗すると、
                // 本当に壊れている 1 か所が失敗の列に埋もれる
                break;
            }
            result.identifier()
                    .filter(identifier -> step.produces())
                    .ifPresent(identifier -> context.put(step.producesKey(), identifier));
        }
        return run;
    }

    private StepResult execute(ScenarioStep step, Map<String, String> context) {
        Instant startedAt = clock.instant();
        long startedNanos = System.nanoTime();
        try {
            String identifier = business.execute(step, Map.copyOf(context));
            return StepResult.succeeded(step, elapsedSince(startedNanos),
                    identifier == null || identifier.isBlank() ? null : identifier, startedAt);
        } catch (BusinessCallFailedException e) {
            // **理由を残す。**「失敗しました」だけでは、経路候補が 0 件なのか
            // 接続先が違うのかを切り分けられない
            return StepResult.failed(step, elapsedSince(startedNanos), e.getMessage(), startedAt);
        } catch (RuntimeException e) {
            // **想定していない失敗も、工程の結果として記録する。**
            //
            // 記録せずに抜けると、その実行は工程を 1 つも終えないまま「実行中」で残り、
            // 二重実行の拒否が永久に効いて**誰もそのシナリオを実行できなくなる**。
            // しかも US35 が見たい「どこまで進んだか」が、失敗したときだけ残らない
            // ——`@Transactional` を置かない理由として挙げたのと同じ結果になる。
            //
            // 実際に届く例: 名簿に無いロール（IllegalStateException）、
            // 引き継いだ識別子が数値でない（NumberFormatException）
            return StepResult.failed(step, elapsedSince(startedNanos),
                    "想定していない失敗です（" + e.getClass().getSimpleName() + ": "
                            + e.getMessage() + "）", startedAt);
        }
    }

    private static Duration elapsedSince(long startedNanos) {
        return Duration.ofNanos(System.nanoTime() - startedNanos);
    }

    /**
     * その日の連番を採る。
     *
     * <p><strong>前置きで数える。</strong>日付をまたぐ範囲検索にすると、境界の解釈が
     * DB の方言で変わる（IT12 で実測）。
     */
    private RunId nextRunId() {
        String prefix = "SIM-" + LocalDate.now(clock).format(DAY) + "-";
        return RunId.of(prefix + "%04d".formatted(runs.countByRunIdPrefix(prefix) + 1));
    }
}
