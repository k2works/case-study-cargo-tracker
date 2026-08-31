package com.example.simulationms.application.internal.commandservices;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.simulationms.application.internal.outboundservices.acl.BusinessCallFailedException;
import com.example.simulationms.application.internal.outboundservices.acl.BusinessGateway;
import com.example.simulationms.domain.model.aggregates.SimulationRun;
import com.example.simulationms.domain.model.valueobjects.BusinessContextKey;
import com.example.simulationms.domain.model.valueobjects.RunId;
import com.example.simulationms.domain.model.valueobjects.RunStatus;
import com.example.simulationms.domain.model.valueobjects.Scenario;
import com.example.simulationms.domain.model.valueobjects.ScenarioStep;
import com.example.simulationms.domain.model.valueobjects.StepResult;
import com.example.simulationms.domain.repository.SimulationRunRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * シナリオを 1 本流す（US34）。
 *
 * <p>ここで固定するのは 3 つ。<strong>本番と同じ出口だけを使うこと</strong>、
 * <strong>失敗しても巻き戻さないこと</strong>、<strong>同じシナリオを二重に流さないこと</strong>。
 */
@DisplayName("業務シミュレーションの実行")
class RunSimulationUseCaseTest {

    private static final Clock CLOCK =
            Clock.fixed(Instant.parse("2026-11-16T01:00:00Z"), ZoneId.of("Asia/Tokyo"));

    /** 記録した順に覚えるだけの記録係。 */
    private static final class FakeRepository implements SimulationRunRepository {

        private final Map<String, SimulationRun> runs = new LinkedHashMap<>();
        private final List<StepResult> appended = new ArrayList<>();

        @Override
        public void create(SimulationRun run) {
            runs.put(run.runId().value(), run);
        }

        @Override
        public void appendResult(RunId runId, StepResult result) {
            appended.add(result);
            runs.computeIfPresent(runId.value(), (key, run) -> run.withResult(result));
        }

        @Override
        public Optional<SimulationRun> findByRunId(RunId runId) {
            return Optional.ofNullable(runs.get(runId.value()));
        }

        @Override
        public List<SimulationRun> findRecent(int limit) {
            return List.copyOf(runs.values());
        }

        @Override
        public Optional<SimulationRun> findRunningByScenario(String scenarioId) {
            return runs.values().stream()
                    .filter(run -> run.scenario().id().equals(scenarioId) && run.running())
                    .findFirst();
        }

        @Override
        public int countByRunIdPrefix(String prefix) {
            return (int) runs.keySet().stream().filter(id -> id.startsWith(prefix)).count();
        }
    }

    /** 呼ばれた工程を覚え、指示された工程で失敗する出口。 */
    private static final class FakeGateway implements BusinessGateway {

        private final List<ScenarioStep> called = new ArrayList<>();
        private final Map<ScenarioStep, String> identifiers = new EnumMap<>(ScenarioStep.class);
        private ScenarioStep failAt;

        @Override
        public String execute(ScenarioStep step, Map<String, String> context) {
            called.add(step);
            if (step == failAt) {
                throw new BusinessCallFailedException(step.label() + " が失敗しました（503）");
            }
            return identifiers.getOrDefault(step, BusinessContextKey.NONE);
        }
    }

    private final FakeRepository repository = new FakeRepository();
    private final FakeGateway gateway = new FakeGateway();
    private final RunSimulationUseCase useCase =
            new RunSimulationUseCase(repository, gateway, CLOCK);

    private static Scenario shortScenario() {
        return Scenario.of("short", List.of(
                ScenarioStep.REGISTER_SHIPPER, ScenarioStep.REGISTER_BOOKING,
                ScenarioStep.REQUEST_ROUTING));
    }

    @Nested
    @DisplayName("通し")
    class Running {

        @Test
        @DisplayName("工程を並び順に実行し、結果を 1 件ずつ記録する")
        void runsEveryStepInOrder() {
            SimulationRun run = useCase.run(shortScenario(), "admin01");

            assertThat(gateway.called).containsExactly(ScenarioStep.REGISTER_SHIPPER,
                    ScenarioStep.REGISTER_BOOKING, ScenarioStep.REQUEST_ROUTING);
            assertThat(run.status()).isEqualTo(RunStatus.COMPLETED);
            assertThat(repository.appended).hasSize(3);
        }

        @Test
        @DisplayName("実行 ID は日付と連番でできている")
        void numbersTheRunByDay() {
            SimulationRun first = useCase.run(shortScenario(), "admin01");
            SimulationRun second = useCase.run(shortScenario(), "admin01");

            assertThat(first.runId().value()).isEqualTo("SIM-20261116-0001");
            assertThat(second.runId().value()).isEqualTo("SIM-20261116-0002");
        }

        /**
         * <strong>引き継ぎは工程が宣言した名前で渡す。</strong>
         *
         * <p>渡し損ねると、後ろの工程が存在しない予約を操作して 404 になる。
         */
        @Test
        @DisplayName("前の工程が生んだ識別子を、次の工程へ引き継ぐ")
        void passesIdentifiersToLaterSteps() {
            gateway.identifiers.put(ScenarioStep.REGISTER_SHIPPER, "42");

            SimulationRun run = useCase.run(shortScenario(), "admin01");

            assertThat(run.identifierOf(ScenarioStep.REGISTER_SHIPPER)).contains("42");
        }
    }

    @Nested
    @DisplayName("失敗")
    class Failing {

        /**
         * <strong>巻き戻さない</strong>（[ADR-030] 決定 5）。
         *
         * <p>それまでに作られた業務データは残る。消すと、どこまで通ったかが分からなくなる。
         */
        @Test
        @DisplayName("途中で失敗したらそこで止まり、それまでの記録は残る")
        void stopsAtTheFailedStepAndKeepsWhatHappened() {
            gateway.failAt = ScenarioStep.REGISTER_BOOKING;

            SimulationRun run = useCase.run(shortScenario(), "admin01");

            assertThat(run.status()).isEqualTo(RunStatus.FAILED);
            assertThat(run.reachedStep()).contains(ScenarioStep.REGISTER_BOOKING);
            assertThat(run.failureReason()).isPresent()
                    .get().asString().contains("503");
            // 後ろの工程は踏まない。踏むと、前提の無い操作が次々に失敗する
            assertThat(gateway.called).doesNotContain(ScenarioStep.REQUEST_ROUTING);
            // それまでの成功は記録に残る
            assertThat(run.identifierOf(ScenarioStep.REGISTER_SHIPPER)).isNotNull();
        }
    }

    @Nested
    @DisplayName("二重実行")
    class Duplicating {

        @Test
        @DisplayName("同じシナリオが実行中なら断り、実行中の ID を案内する")
        void refusesASecondRunOfTheSameScenario() {
            Scenario scenario = shortScenario();
            gateway.failAt = null;
            // 1 本目を実行中のまま残す（工程を 1 つも終えない）
            RunId running = RunId.of("SIM-20261116-0009");
            repository.create(SimulationRun.start(running, scenario, "admin01", CLOCK.instant()));

            assertThatThrownBy(() -> useCase.run(scenario, "admin01"))
                    .isInstanceOf(SimulationAlreadyRunningException.class)
                    .hasMessageContaining("SIM-20261116-0009");
        }

        @Test
        @DisplayName("終わった実行は二重実行にあたらない")
        void allowsANewRunAfterTheEarlierOneFinished() {
            useCase.run(shortScenario(), "admin01");

            assertThat(useCase.run(shortScenario(), "admin01").status())
                    .isEqualTo(RunStatus.COMPLETED);
        }
    }
}
