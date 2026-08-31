package com.example.simulationms.application.internal.queryservices;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.simulationms.domain.model.aggregates.SimulationRun;
import com.example.simulationms.domain.model.valueobjects.RunId;
import com.example.simulationms.domain.model.valueobjects.Scenario;
import com.example.simulationms.domain.model.valueobjects.ScenarioStep;
import com.example.simulationms.domain.model.valueobjects.StepResult;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 実行の統計（US37-8）。
 *
 * <p><strong>失敗した工程の分布が肝である。</strong>件数だけでは「たくさん落ちている」
 * としか分からない。どの工程で落ちているかが分かって初めて、直す場所が決まる。
 */
@DisplayName("実行の統計")
class SimulationStatisticsTest {

    private static final Instant AT = Instant.parse("2026-12-07T01:00:00Z");

    private static Scenario scenario() {
        return Scenario.of("stats", List.of(ScenarioStep.REGISTER_SHIPPER,
                ScenarioStep.REGISTER_BOOKING));
    }

    private static SimulationRun completed(String runId) {
        return SimulationRun.start(RunId.of(runId), scenario(), "admin01", AT)
                .withResult(StepResult.succeeded(ScenarioStep.REGISTER_SHIPPER,
                        Duration.ofMillis(10), "1", AT))
                .withResult(StepResult.succeeded(ScenarioStep.REGISTER_BOOKING,
                        Duration.ofMillis(10), "2", AT));
    }

    private static SimulationRun failedAt(String runId, ScenarioStep step) {
        SimulationRun run = SimulationRun.start(RunId.of(runId), scenario(), "admin01", AT);
        if (step == ScenarioStep.REGISTER_BOOKING) {
            run = run.withResult(StepResult.succeeded(ScenarioStep.REGISTER_SHIPPER,
                    Duration.ofMillis(10), "1", AT));
        }
        return run.withResult(StepResult.failed(step, Duration.ofMillis(10), "失敗", AT));
    }

    private static SimulationRun running(String runId) {
        return SimulationRun.start(RunId.of(runId), scenario(), "admin01", AT);
    }

    @Test
    @DisplayName("実行件数と、成功・失敗の内訳を数える")
    void countsRunsByOutcome() {
        SimulationStatistics statistics = SimulationStatistics.of(List.of(
                completed("SIM-20261207-0001"),
                failedAt("SIM-20261207-0002", ScenarioStep.REGISTER_BOOKING),
                running("SIM-20261207-0003")));

        assertThat(statistics.total()).isEqualTo(3);
        assertThat(statistics.succeeded()).isEqualTo(1);
        assertThat(statistics.failed()).isEqualTo(1);
        assertThat(statistics.running()).isEqualTo(1);
    }

    /**
     * <strong>どの工程で落ちたかを数える。</strong>件数だけでは直す場所が決まらない。
     */
    @Test
    @DisplayName("失敗した工程の分布を数える")
    void countsFailuresByStep() {
        SimulationStatistics statistics = SimulationStatistics.of(List.of(
                failedAt("SIM-20261207-0001", ScenarioStep.REGISTER_SHIPPER),
                failedAt("SIM-20261207-0002", ScenarioStep.REGISTER_BOOKING),
                failedAt("SIM-20261207-0003", ScenarioStep.REGISTER_BOOKING),
                completed("SIM-20261207-0004")));

        assertThat(statistics.failuresByStep())
                .containsEntry(ScenarioStep.REGISTER_BOOKING, 2)
                .containsEntry(ScenarioStep.REGISTER_SHIPPER, 1);
    }

    /**
     * <strong>多い順に並べる。</strong>直す場所を決める材料なので、
     * 読む人が並べ替えなくてよい形にする。
     */
    @Test
    @DisplayName("失敗した工程は、多い順に並ぶ")
    void ordersFailuresByCount() {
        SimulationStatistics statistics = SimulationStatistics.of(List.of(
                failedAt("SIM-20261207-0001", ScenarioStep.REGISTER_SHIPPER),
                failedAt("SIM-20261207-0002", ScenarioStep.REGISTER_BOOKING),
                failedAt("SIM-20261207-0003", ScenarioStep.REGISTER_BOOKING)));

        assertThat(statistics.failuresByStep().keySet())
                .containsExactly(ScenarioStep.REGISTER_BOOKING, ScenarioStep.REGISTER_SHIPPER);
    }

    /** 1 件も無い状態を、0 件として読めるようにする。 */
    @Test
    @DisplayName("実行が無ければ、すべて 0 になる")
    void handlesNoRuns() {
        SimulationStatistics statistics = SimulationStatistics.of(List.of());

        assertThat(statistics.total()).isZero();
        assertThat(statistics.failuresByStep()).isEmpty();
    }
}
