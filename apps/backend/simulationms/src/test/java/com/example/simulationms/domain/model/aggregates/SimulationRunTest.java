package com.example.simulationms.domain.model.aggregates;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.simulationms.domain.model.valueobjects.RunId;
import com.example.simulationms.domain.model.valueobjects.RunStatus;
import com.example.simulationms.domain.model.valueobjects.Scenario;
import com.example.simulationms.domain.model.valueobjects.ScenarioStep;
import com.example.simulationms.domain.model.valueobjects.StepResult;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("業務シミュレーションの実行")
class SimulationRunTest {

    private static final RunId ID = RunId.of("SIM-20261116-0001");
    private static final Instant STARTED = Instant.parse("2026-11-16T01:00:00Z");

    private static SimulationRun started() {
        return SimulationRun.start(ID, Scenario.standardTransport(), "admin01", STARTED);
    }

    private static StepResult succeeded(ScenarioStep step, String identifier) {
        return StepResult.succeeded(step, Duration.ofMillis(120), identifier);
    }

    @Nested
    @DisplayName("開始")
    class Starting {

        @Test
        @DisplayName("開始した実行は、実行中で工程の結果を持たない")
        void startsRunning() {
            SimulationRun run = started();

            assertThat(run.status()).isEqualTo(RunStatus.RUNNING);
            assertThat(run.results()).isEmpty();
            assertThat(run.startedBy()).isEqualTo("admin01");
            assertThat(run.finishedAt()).isEmpty();
        }

        @Test
        @DisplayName("誰が始めたか分からない実行は作れない")
        void requiresStartedBy() {
            Scenario scenario = Scenario.standardTransport();

            assertThatThrownBy(() -> SimulationRun.start(ID, scenario, " ", STARTED))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Nested
    @DisplayName("工程の記録")
    class Recording {

        @Test
        @DisplayName("成功した工程を順に記録し、生成した識別子を残す")
        void recordsSucceededSteps() {
            SimulationRun run = started()
                    .record(succeeded(ScenarioStep.REGISTER_SHIPPER, "SIM-0001"))
                    .record(succeeded(ScenarioStep.REGISTER_BOOKING, "BKG-2026000001"));

            assertThat(run.status()).isEqualTo(RunStatus.RUNNING);
            assertThat(run.results()).extracting(StepResult::step)
                    .containsExactly(ScenarioStep.REGISTER_SHIPPER, ScenarioStep.REGISTER_BOOKING);
            assertThat(run.identifierOf(ScenarioStep.REGISTER_BOOKING))
                    .contains("BKG-2026000001");
        }

        @Test
        @DisplayName("シナリオが定めていない工程は記録できない")
        void rejectsStepsOutsideTheScenario() {
            SimulationRun run = SimulationRun.start(ID,
                    Scenario.of("only-booking", List.of(ScenarioStep.REGISTER_SHIPPER)),
                    "admin01", STARTED);

            StepResult settled = succeeded(ScenarioStep.SETTLE, "INV-1");

            assertThatThrownBy(() -> run.record(settled))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("SETTLE");
        }

        @Test
        @DisplayName("同じ工程は二度記録できない")
        void rejectsDuplicateSteps() {
            SimulationRun run = started().record(succeeded(ScenarioStep.REGISTER_SHIPPER, "S-1"));

            StepResult again = succeeded(ScenarioStep.REGISTER_SHIPPER, "S-2");

            assertThatThrownBy(() -> run.record(again))
                    .isInstanceOf(IllegalStateException.class);
        }
    }

    @Nested
    @DisplayName("失敗")
    class Failing {

        @Test
        @DisplayName("失敗した工程で止まり、それまでの結果は残る（巻き戻さない）")
        void keepsResultsWhenAStepFails() {
            SimulationRun run = started()
                    .record(succeeded(ScenarioStep.REGISTER_SHIPPER, "SIM-0001"))
                    .record(StepResult.failed(ScenarioStep.REGISTER_BOOKING,
                            Duration.ofMillis(90), "500 予約の登録に失敗しました"));

            assertThat(run.status()).isEqualTo(RunStatus.FAILED);
            assertThat(run.results()).hasSize(2);
            assertThat(run.identifierOf(ScenarioStep.REGISTER_SHIPPER)).contains("SIM-0001");
            assertThat(run.reachedStep()).contains(ScenarioStep.REGISTER_BOOKING);
            assertThat(run.failureReason()).contains("500 予約の登録に失敗しました");
        }

        @Test
        @DisplayName("失敗した実行に、続きの工程は記録できない")
        void rejectsStepsAfterFailure() {
            SimulationRun run = started().record(StepResult.failed(ScenarioStep.REGISTER_SHIPPER,
                    Duration.ofMillis(90), "接続できません"));

            StepResult next = succeeded(ScenarioStep.REGISTER_BOOKING, "B-1");

            assertThatThrownBy(() -> run.record(next))
                    .isInstanceOf(IllegalStateException.class);
        }
    }

    @Nested
    @DisplayName("完了")
    class Completing {

        @Test
        @DisplayName("シナリオの全工程を終えると完了する")
        void completesWhenEveryStepSucceeded() {
            SimulationRun run = SimulationRun.start(ID,
                    Scenario.of("short", List.of(ScenarioStep.REGISTER_SHIPPER,
                            ScenarioStep.REGISTER_BOOKING)), "admin01", STARTED)
                    .record(succeeded(ScenarioStep.REGISTER_SHIPPER, "SIM-0001"))
                    .record(succeeded(ScenarioStep.REGISTER_BOOKING, "BKG-2026000001"));

            assertThat(run.status()).isEqualTo(RunStatus.COMPLETED);
            assertThat(run.reachedStep()).contains(ScenarioStep.REGISTER_BOOKING);
        }

        @Test
        @DisplayName("完了した実行は、終了時刻を持つ")
        void recordsFinishedAt() {
            SimulationRun run = SimulationRun.start(ID,
                    Scenario.of("short", List.of(ScenarioStep.REGISTER_SHIPPER)),
                    "admin01", STARTED)
                    .record(StepResult.succeeded(ScenarioStep.REGISTER_SHIPPER,
                            Duration.ofMillis(120), "SIM-0001",
                            Instant.parse("2026-11-16T01:00:05Z")));

            assertThat(run.finishedAt()).contains(Instant.parse("2026-11-16T01:00:05Z"));
        }
    }

    @Nested
    @DisplayName("進行中の判定")
    class InProgress {

        @Test
        @DisplayName("実行中は二重実行を断る材料になる")
        void tellsWhetherItIsRunning() {
            assertThat(started().running()).isTrue();
            assertThat(started().record(StepResult.failed(ScenarioStep.REGISTER_SHIPPER,
                    Duration.ofMillis(1), "だめ")).running()).isFalse();
        }
    }
}
