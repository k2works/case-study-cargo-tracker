package com.example.cargotracker.simulation.domain.model.aggregates;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.cargotracker.shared.domain.error.BusinessRuleViolation;
import com.example.cargotracker.shared.domain.error.IllegalTransition;
import com.example.cargotracker.simulation.domain.model.valueobjects.RunStatus;
import com.example.cargotracker.simulation.domain.model.valueobjects.Scenario;
import com.example.cargotracker.simulation.domain.model.valueobjects.StepKind;
import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 実行の整合（US33 §受入基準 5 / US34 §受入基準 3・4）。
 *
 * <p><b>業務の不変条件は増えない。</b> ここが守るのは実行そのものの整合で、
 * 「工程は宣言した順に記録する」「終わった実行に工程を足さない」「失敗しても
 * それまでの記録を消さない」の 3 つである（[ADR-0020]）。</p>
 */
class SimulationRunTest {

    private static final Instant NOW = Instant.parse("2026-09-14T01:00:00Z");

    private static SimulationRun started() {
        return SimulationRun.start("run-1", Scenario.STANDARD, null, "admin01", NOW);
    }

    @Test
    @DisplayName("US33 §1: 始めた実行は、シナリオの工程を宣言した順に持つ")
    void startsWithTheScenarioSteps() {
        SimulationRun run = started();

        assertThat(run.status()).isEqualTo(RunStatus.RUNNING);
        assertThat(run.plannedSteps()).isEqualTo(Scenario.STANDARD.steps());
        assertThat(run.recordedSteps()).isEmpty();
    }

    @Test
    @DisplayName("US34 §1: 工程の結果は、所要時間と生成した識別子つきで残る")
    void recordsEachStepWithElapsedAndProducedId() {
        SimulationRun run = started();

        run.recordSuccess(StepKind.REGISTER_SHIPPER, Duration.ofMillis(120), Duration.ZERO, "SHP-0001", NOW);

        assertThat(run.recordedSteps()).hasSize(1);
        var step = run.recordedSteps().get(0);
        assertThat(step.stepNo()).isEqualTo(1);
        assertThat(step.kind()).isEqualTo(StepKind.REGISTER_SHIPPER);
        assertThat(step.elapsed()).isEqualTo(Duration.ofMillis(120));
        assertThat(step.producedId()).isEqualTo("SHP-0001");
    }

    @Test
    @DisplayName("工程は宣言した順にしか記録できない（飛ばして記録しない）")
    void refusesStepsOutOfOrder() {
        SimulationRun run = started();

        assertThatThrownBy(() ->
                run.recordSuccess(StepKind.RECORD_PAYMENT, Duration.ZERO, Duration.ZERO, null, NOW))
                .isInstanceOf(IllegalTransition.class)
                .hasMessageContaining("荷主の登録");
    }

    @Test
    @DisplayName("US34 §3: 失敗した工程は理由つきで残り、実行はそこで終わる")
    void failureStopsTheRunAndKeepsTheReason() {
        SimulationRun run = started();
        run.recordSuccess(StepKind.REGISTER_SHIPPER, Duration.ofMillis(10), Duration.ZERO, "SHP-0001", NOW);

        run.recordFailure(StepKind.REGISTER_BOOKING, Duration.ofMillis(20), Duration.ZERO,
                422, "出発地と目的地が同じです", NOW);

        assertThat(run.status()).isEqualTo(RunStatus.FAILED);
        assertThat(run.recordedSteps()).hasSize(2);
        var failed = run.recordedSteps().get(1);
        assertThat(failed.failureStatus()).isEqualTo(422);
        assertThat(failed.failureMessage()).contains("出発地と目的地が同じ");
        // **それまでの記録は消さない。** どこまで進んだかを追えることが目的である。
        assertThat(run.recordedSteps().get(0).producedId()).isEqualTo("SHP-0001");
    }

    @Test
    @DisplayName("終わった実行には工程を足さない")
    void refusesStepsAfterTheRunEnded() {
        SimulationRun run = started();
        run.recordFailure(StepKind.REGISTER_SHIPPER, Duration.ZERO, Duration.ZERO, 500, "落ちた", NOW);

        assertThatThrownBy(() ->
                run.recordSuccess(StepKind.REGISTER_BOOKING, Duration.ZERO, Duration.ZERO, null, NOW))
                .isInstanceOf(IllegalTransition.class)
                .hasMessageContaining("終わって");
    }

    @Test
    @DisplayName("US33 §1: 最後の工程まで成功したら、実行は成功で終わる")
    void succeedsWhenEveryStepIsRecorded() {
        SimulationRun run = started();
        for (StepKind kind : Scenario.STANDARD.steps()) {
            run.recordSuccess(kind, Duration.ofMillis(5), Duration.ZERO, null, NOW);
        }

        assertThat(run.status()).isEqualTo(RunStatus.SUCCEEDED);
    }

    @Test
    @DisplayName("始めた人が要る（誰が流したか分からない実行を作らない）")
    void requiresWhoStartedIt() {
        assertThatThrownBy(() ->
                SimulationRun.start("run-1", Scenario.STANDARD, null, "  ", NOW))
                .isInstanceOf(BusinessRuleViolation.class)
                .hasMessageContaining("実行した人");
    }

    @Test
    @DisplayName("予期しない中断でも実行は決着する（実行中のまま残さない）")
    void abortsWithoutRecordingAStep() {
        // **決着しない実行は、そのシナリオを二度と流せなくする。** 二重実行の守りは
        // 「RUNNING が 1 本」なので、掴んだまま離さない実行が 1 本あれば十分である。
        SimulationRun run = started();
        run.recordSuccess(StepKind.REGISTER_SHIPPER, Duration.ofMillis(5), Duration.ZERO, "SHP-1", NOW);

        run.abort(NOW);

        assertThat(run.status()).isEqualTo(RunStatus.FAILED);
        assertThat(run.finishedAt()).isEqualTo(NOW);
        // **それまでの記録は消さない**（US34 §受入基準 3）。
        assertThat(run.recordedSteps()).hasSize(1);
    }

    @Test
    @DisplayName("終わった実行は中断で上書きしない")
    void doesNotAbortFinishedRun() {
        SimulationRun run = started();
        for (StepKind kind : Scenario.STANDARD.steps()) {
            run.recordSuccess(kind, Duration.ofMillis(5), Duration.ZERO, null, NOW);
        }

        run.abort(NOW.plusSeconds(1));

        assertThat(run.status()).isEqualTo(RunStatus.SUCCEEDED);
    }
}
