package com.example.simulationms.domain.model.aggregates;

import com.example.simulationms.domain.model.valueobjects.RunId;
import com.example.simulationms.domain.model.valueobjects.RunStatus;
import com.example.simulationms.domain.model.valueobjects.Scenario;
import com.example.simulationms.domain.model.valueobjects.ScenarioStep;
import com.example.simulationms.domain.model.valueobjects.StepResult;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * 業務シミュレーションの 1 回の実行（US34・US35）。
 *
 * <p><strong>失敗しても巻き戻さない</strong>（[ADR-030] 決定 5）。工程の結果は足すだけで、
 * 消したり書き換えたりしない。どこまで進んだかを追えることが US35 の目的であり、
 * 巻き戻すと失敗の痕跡が消える。IT12 では `@Transactional` が入金の記録ごと巻き戻し、
 * 経理担当者が何度押しても記録できない経路になった——その裏返しである。
 */
public final class SimulationRun {

    private final RunId runId;
    private final Scenario scenario;
    private final String startedBy;
    private final Instant startedAt;
    private final List<StepResult> results;

    private SimulationRun(RunId runId, Scenario scenario, String startedBy, Instant startedAt,
            List<StepResult> results) {
        this.runId = runId;
        this.scenario = scenario;
        this.startedBy = startedBy;
        this.startedAt = startedAt;
        this.results = List.copyOf(results);
    }

    /** 実行を開始する。 */
    public static SimulationRun start(RunId runId, Scenario scenario, String startedBy,
            Instant startedAt) {
        if (runId == null || scenario == null || startedAt == null) {
            throw new IllegalArgumentException("実行 ID・シナリオ・開始時刻は必須です");
        }
        if (startedBy == null || startedBy.isBlank()) {
            throw new IllegalArgumentException("誰が始めたかを記録しない実行は作れません");
        }
        return new SimulationRun(runId, scenario, startedBy, startedAt, List.of());
    }

    /**
     * 永続化された行から復元する。
     *
     * <p><strong>復元では検査しない</strong>（不変条件は受け入れ時に見る）。列が無かった
     * ころの行や、シナリオ定義を変える前の行が読めなくなる形にしない。
     */
    public static SimulationRun restore(RunId runId, Scenario scenario, String startedBy,
            Instant startedAt, List<StepResult> results) {
        return new SimulationRun(runId, scenario, startedBy, startedAt, results);
    }

    /**
     * 工程の結果を記録する。
     *
     * <p>シナリオが定めていない工程は受け付けない。受け付けると、実行結果が
     * 「そのシナリオを流した記録」ではなくなる。
     */
    public SimulationRun withResult(StepResult result) {
        if (result == null) {
            throw new IllegalArgumentException("工程の結果は必須です");
        }
        if (!scenario.includes(result.step())) {
            throw new IllegalArgumentException(
                    "シナリオ %s に含まれない工程です: %s".formatted(scenario.id(), result.step()));
        }
        if (status() != RunStatus.RUNNING) {
            throw new IllegalStateException(
                    "実行は既に終わっています（%s）。続きの工程は記録できません".formatted(status()));
        }
        if (results.stream().anyMatch(recorded -> recorded.step() == result.step())) {
            throw new IllegalStateException("同じ工程は二度記録できません: " + result.step());
        }
        List<StepResult> appended = new ArrayList<>(results);
        appended.add(result);
        return new SimulationRun(runId, scenario, startedBy, startedAt, appended);
    }

    /**
     * 止まったきりとみなすまでの時間。
     *
     * <p><strong>判定はここ 1 つに置く。</strong>二重実行の拒否・継続実行の停止の
     * 見切り・統計の数え方が、それぞれ別の値を持つと食い違う。
     *
     * <p>実行 1 本は 14〜18 工程で 10 秒前後。配備や Pod の再起動で途中終了した
     * 実行は、工程の結果から導くと永久に「実行中」で残る。
     */
    public static final java.time.Duration STALE_AFTER = java.time.Duration.ofMinutes(15);

    /**
     * 止まったきりか。
     *
     * <p>実行中のまま、最後の記録が {@code staleBefore} より古いもの。
     * <strong>実行中と中断は区別する</strong>——「実行中 7 件」と出ていると、
     * 管理者は止めてよいのかまだ待つのかを判断できない。
     */
    public boolean abandoned(Instant staleBefore) {
        return status() == RunStatus.RUNNING && lastActivityAt().isBefore(staleBefore);
    }

    /** 最後に何かが記録された時刻。工程が 1 つも無ければ開始時刻。 */
    public Instant lastActivityAt() {
        return results.isEmpty() ? startedAt : results.getLast().recordedAt();
    }

    public RunStatus status() {
        if (results.stream().anyMatch(StepResult::failed)) {
            return RunStatus.FAILED;
        }
        return results.size() == scenario.steps().size() ? RunStatus.COMPLETED : RunStatus.RUNNING;
    }

    /** 実行中か。同じシナリオの二重実行を断る根拠になる（US34-5）。 */
    public boolean running() {
        return status() == RunStatus.RUNNING;
    }

    /** どこまで進んだか。まだ 1 工程も記録していなければ空。 */
    public Optional<ScenarioStep> reachedStep() {
        return results.isEmpty() ? Optional.empty() : Optional.of(results.getLast().step());
    }

    /** その工程が生成した識別子（予約番号・追跡番号・請求番号）。 */
    public Optional<String> identifierOf(ScenarioStep step) {
        return results.stream()
                .filter(result -> result.step() == step)
                .findFirst()
                .flatMap(StepResult::identifier);
    }

    /** 失敗した理由。成功・実行中なら空。 */
    public Optional<String> failureReason() {
        return results.stream()
                .filter(StepResult::failed)
                .findFirst()
                .map(StepResult::failureReason);
    }

    /** 終了時刻。最後に記録した工程の時刻を使う。実行中なら空。 */
    public Optional<Instant> finishedAt() {
        if (status() == RunStatus.RUNNING || results.isEmpty()) {
            return Optional.empty();
        }
        return Optional.ofNullable(results.getLast().recordedAt());
    }

    public RunId runId() {
        return runId;
    }

    public Scenario scenario() {
        return scenario;
    }

    public String startedBy() {
        return startedBy;
    }

    public Instant startedAt() {
        return startedAt;
    }

    public List<StepResult> results() {
        return results;
    }
}
