package com.example.cargotracker.simulation.domain.model.aggregates;

import com.example.cargotracker.shared.domain.error.BusinessRuleViolation;
import com.example.cargotracker.shared.domain.error.IllegalTransition;
import com.example.cargotracker.simulation.domain.model.valueobjects.RunStatus;
import com.example.cargotracker.simulation.domain.model.valueobjects.Scenario;
import com.example.cargotracker.simulation.domain.model.valueobjects.StepKind;
import com.example.cargotracker.simulation.domain.model.valueobjects.StepOutcome;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * 業務シミュレーションの実行（UC23 / US33・US34）。
 *
 * <p><b>Event Sourcing は適用しない</b>（[ADR-0020] 決定 3）。実行の記録は業務の
 * 事実ではなく、監査もリプレイも要らない。ここは**普通のオブジェクト**で、
 * 永続化は呼び出し側（アプリケーション層）が行う。</p>
 *
 * <p><b>業務の不変条件は増えない。</b> 守るのは実行そのものの整合である。</p>
 *
 * <ol>
 *   <li>工程は<b>宣言した順に</b>記録する（飛ばして記録しない）</li>
 *   <li><b>終わった実行に工程を足さない</b></li>
 *   <li>失敗しても<b>それまでの記録を消さない</b>——どこまで進んだかを追える
 *       ことが US34 の目的である</li>
 * </ol>
 */
public final class SimulationRun {

    private final String runId;
    private final Scenario scenario;
    private final Long seed;
    private final String startedBy;
    private final Instant startedAt;
    private final List<RecordedStep> recordedSteps = new ArrayList<>();

    private RunStatus status = RunStatus.RUNNING;
    private Instant finishedAt;

    private SimulationRun(String runId, Scenario scenario, Long seed, String startedBy,
            Instant startedAt) {
        this.runId = runId;
        this.scenario = scenario;
        this.seed = seed;
        this.startedBy = startedBy;
        this.startedAt = startedAt;
    }

    /**
     * 実行を始める。
     *
     * @param seed 乱数の種。<b>手で選んだ実行では {@code null}</b>（US36 で使う）
     */
    public static SimulationRun start(String runId, Scenario scenario, Long seed,
            String startedBy, Instant startedAt) {
        if (startedBy == null || startedBy.isBlank()) {
            // 誰が流したか分からない実行は、結果を誰にも問い合わせられない。
            throw new BusinessRuleViolation("実行した人は必須です");
        }
        return new SimulationRun(runId, scenario, seed, startedBy.trim(), startedAt);
    }

    /** 工程が通った。 */
    public void recordSuccess(StepKind kind, Duration elapsed, String producedId,
            Instant occurredAt) {
        record(new RecordedStep(nextStepNo(kind), kind, StepOutcome.SUCCEEDED, elapsed,
                producedId, null, null, occurredAt));
        if (recordedSteps.size() == scenario.steps().size()) {
            this.status = RunStatus.SUCCEEDED;
            this.finishedAt = occurredAt;
        }
    }

    /**
     * 工程が通らなかった。<b>実行はここで終わる</b>。
     *
     * <p><b>それまでに作られた業務データは取り消さない</b>（US34 §受入基準 3）。
     * どこまで進んだかを追えるようにするためである。</p>
     */
    public void recordFailure(StepKind kind, Duration elapsed, Integer failureStatus,
            String failureMessage, Instant occurredAt) {
        record(new RecordedStep(nextStepNo(kind), kind, StepOutcome.FAILED, elapsed,
                null, failureStatus, failureMessage, occurredAt));
        this.status = RunStatus.FAILED;
        this.finishedAt = occurredAt;
    }

    private void record(RecordedStep step) {
        recordedSteps.add(step);
    }

    /**
     * 次の工程の番号。<b>宣言した順であることをここで確かめる</b>。
     *
     * <p>順を飛ばして記録できると、「どの工程まで進んだか」が読めなくなる。</p>
     */
    private int nextStepNo(StepKind kind) {
        if (status.isFinished()) {
            throw new IllegalTransition("実行 " + runId + " は終わっています（"
                    + status.label() + "）。工程は足せません");
        }
        StepKind expected = scenario.steps().get(recordedSteps.size());
        if (expected != kind) {
            throw new IllegalTransition("次の工程は「" + expected.label()
                    + "」です（受け取ったのは「" + kind.label() + "」）");
        }
        return recordedSteps.size() + 1;
    }

    /** 実行の識別子。 */
    public String runId() {
        return runId;
    }

    /** シナリオ。 */
    public Scenario scenario() {
        return scenario;
    }

    /** 乱数の種。手で選んだ実行では {@code null}。 */
    public Long seed() {
        return seed;
    }

    /** 実行した人。 */
    public String startedBy() {
        return startedBy;
    }

    /** 始めた時刻。 */
    public Instant startedAt() {
        return startedAt;
    }

    /** 終わった時刻。<b>走っているあいだは {@code null}</b>。 */
    public Instant finishedAt() {
        return finishedAt;
    }

    /** いまの状態。 */
    public RunStatus status() {
        return status;
    }

    /** 予定の工程（シナリオが持つ並び）。 */
    public List<StepKind> plannedSteps() {
        return scenario.steps();
    }

    /** 記録した工程。<b>失敗しても消さない</b>。 */
    public List<RecordedStep> recordedSteps() {
        return List.copyOf(recordedSteps);
    }

    /**
     * 記録した工程 1 件（US34 §受入基準 1・2）。
     *
     * @param producedId その工程が生成した識別子（予約番号・追跡番号・請求番号）。
     *     <b>ここから業務画面へ行ける</b>ことが §受入基準 5 である
     */
    public record RecordedStep(
            int stepNo,
            StepKind kind,
            StepOutcome outcome,
            Duration elapsed,
            String producedId,
            Integer failureStatus,
            String failureMessage,
            Instant occurredAt) {
    }
}
