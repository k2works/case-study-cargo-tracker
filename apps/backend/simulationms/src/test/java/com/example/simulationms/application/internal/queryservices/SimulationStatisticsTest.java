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

    /** 見切りの手前。**実行中を中断として数えないための境目**。 */
    private static final Instant NOT_STALE = AT.minusSeconds(1);

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
                running("SIM-20261207-0003")), NOT_STALE);

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
                completed("SIM-20261207-0004")), NOT_STALE);

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
                failedAt("SIM-20261207-0003", ScenarioStep.REGISTER_BOOKING)), NOT_STALE);

        assertThat(statistics.failuresByStep().keySet())
                .containsExactly(ScenarioStep.REGISTER_BOOKING, ScenarioStep.REGISTER_SHIPPER);
    }

    /** 1 件も無い状態を、0 件として読めるようにする。 */
    @Test
    @DisplayName("実行が無ければ、すべて 0 になる")
    void handlesNoRuns() {
        SimulationStatistics statistics = SimulationStatistics.of(List.of(), NOT_STALE);

        assertThat(statistics.total()).isZero();
        assertThat(statistics.failuresByStep()).isEmpty();
    }

    /**
     * <strong>止まったきりの実行を「実行中」に数えない</strong>（IT15 のレビュー指摘）。
     *
     * <p>配備や Pod の再起動で途中終了した実行は、工程の結果から導くと永久に
     * 「実行中」で残る。「実行中 7 件」と出ていれば、管理者は止めてよいのか
     * まだ待つのかを判断できない。
     *
     * <p><strong>見切りの判定は 1 つに置く。</strong>二重実行の拒否・停止の見切りと
     * 同じ {@link SimulationRun#STALE_AFTER} を使う——2 か所で別に持つと、
     * 片方だけ変えたときに食い違う。
     */
    @Test
    @DisplayName("止まったきりの実行は、実行中ではなく中断として数える")
    void countsAbandonedRunsSeparately() {
        SimulationRun first = running("SIM-20261207-0001");
        SimulationRun second = running("SIM-20261207-0002");

        // 見切りの境目より新しい記録なので、どちらもまだ実行中
        SimulationStatistics statistics =
                SimulationStatistics.of(List.of(first, second), AT.minusSeconds(1));

        assertThat(statistics.running()).isEqualTo(2);
        assertThat(statistics.abandoned()).isZero();

        // 境目を過ぎると中断として数える
        SimulationStatistics after =
                SimulationStatistics.of(List.of(first, second),
                        AT.plus(SimulationRun.STALE_AFTER));

        assertThat(after.running()).isZero();
        assertThat(after.abandoned()).isEqualTo(2);
        assertThat(after.total()).isEqualTo(2);
    }
}
