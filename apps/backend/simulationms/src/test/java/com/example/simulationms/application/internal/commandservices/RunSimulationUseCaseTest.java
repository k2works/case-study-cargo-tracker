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
import com.example.simulationms.domain.model.valueobjects.ScenarioRequest;
import com.example.simulationms.domain.model.valueobjects.Seed;
import com.example.simulationms.domain.model.valueobjects.SessionId;
import com.example.simulationms.domain.model.valueobjects.ScenarioStep;
import com.example.simulationms.domain.model.valueobjects.StepResult;
import com.example.simulationms.domain.repository.RunIdAlreadyTakenException;
import com.example.simulationms.domain.repository.SimulationRunRepository;
import java.time.Clock;
import java.time.Duration;
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

        /** 「別の実行に先を越される」回数。負なら毎回越される。 */
        private int stolen;

        /**
         * 本物と同じ厳しさで断る。
         *
         * <p><strong>甘い記録係は、本物が断る入力を通す。</strong>黙って上書きすると、
         * 採番が衝突しても緑のままになる——実 DB では一意制約が断る。
         */
        @Override
        public void create(SimulationRun run,
                com.example.simulationms.domain.model.valueobjects.Seed seed,
                com.example.simulationms.domain.model.valueobjects.SessionId sessionId) {
            if (stolen != 0) {
                // 同時に始まった別の実行が、この番号を先に採った。
                if (stolen > 0) {
                    stolen--;
                }
                runs.put(run.runId().value(), run);
                throw new RunIdAlreadyTakenException(run.runId().value(), null);
            }
            if (runs.containsKey(run.runId().value())) {
                throw new RunIdAlreadyTakenException(run.runId().value(), null);
            }
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

        /** **本物と同じ規則で絞る**——甘くすると、本物の絞り漏れを素通りさせる。 */
        @Override
        public List<SimulationRun> findBetween(Instant from, Instant to, int limit) {
            return runs.values().stream()
                    .filter(run -> from == null || !run.startedAt().isBefore(from))
                    .filter(run -> to == null || run.startedAt().isBefore(to))
                    .limit(limit)
                    .toList();
        }

        @Override
        public Optional<SimulationRun> findRunningByScenario(Scenario scenario,
                Instant staleBefore) {
            return runs.values().stream()
                    .filter(run -> run.scenario().id().equals(scenario.id()) && run.running())
                    .filter(run -> lastActivity(run).isAfter(staleBefore)
                            || lastActivity(run).equals(staleBefore))
                    .findFirst();
        }

        private static Instant lastActivity(SimulationRun run) {
            return run.results().isEmpty() ? run.startedAt()
                    : run.results().getLast().recordedAt();
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

        /** 想定していない失敗を注入する工程。 */
        private ScenarioStep breakAt;

        /** 受け取った引き継ぎ（最後に呼ばれた工程のもの）。 */
        private final Map<ScenarioStep, Map<String, String>> received =
                new EnumMap<>(ScenarioStep.class);

        @Override
        public String execute(ScenarioStep step, Map<String, String> context) {
            called.add(step);
            received.put(step, context);
            if (step == breakAt) {
                throw new IllegalStateException("この工程を踏む利用者が設定されていません");
            }
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
            // **受け取った側で確かめる。**生成した側だけを見ると、引き継ぎを外しても緑になる
            assertThat(gateway.received.get(ScenarioStep.REGISTER_BOOKING))
                    .as("荷主の識別子が次の工程へ渡っていない")
                    .containsEntry(BusinessContextKey.SHIPPER_ID, "42");
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
            // それまでの成功は記録に残る（Optional 相手に isNotNull を書くと常に真になる）
            assertThat(run.results()).extracting(StepResult::step)
                    .contains(ScenarioStep.REGISTER_SHIPPER);
        }

        /**
         * <strong>想定していない失敗も記録して止まる。</strong>
         *
         * <p>記録せずに抜けると、その実行は工程を 1 つも終えないまま「実行中」で残り、
         * 二重実行の拒否が永久に効いて<strong>誰もそのシナリオを実行できなくなる</strong>。
         */
        @Test
        @DisplayName("想定していない例外も、その工程の失敗として記録する")
        void recordsUnexpectedFailuresToo() {
            gateway.breakAt = ScenarioStep.REGISTER_BOOKING;

            SimulationRun run = useCase.run(shortScenario(), "admin01");

            assertThat(run.status()).isEqualTo(RunStatus.FAILED);
            assertThat(run.failureReason()).isPresent()
                    .get().asString().contains("IllegalStateException");
            assertThat(run.running())
                    .as("記録せずに抜けると実行中のまま残り、二重実行の拒否が永久に効く")
                    .isFalse();
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
            repository.create(SimulationRun.start(running, scenario, "admin01", CLOCK.instant()),
                    com.example.simulationms.domain.model.valueobjects.Seed.of(0L), null);

            assertThatThrownBy(() -> useCase.run(scenario, "admin01"))
                    .isInstanceOf(SimulationAlreadyRunningException.class)
                    .hasMessageContaining("SIM-20261116-0009");
        }

        /**
         * <strong>止まったきりの実行は、実行中とみなさない。</strong>
         *
         * <p>残したままにすると、そのシナリオは二度と実行できなくなり、
         * 復旧手段が DB を手で触ることしか無くなる。
         */
        @Test
        @DisplayName("長く音沙汰の無い実行は、二重実行の理由にしない")
        void ignoresAStaleRun() {
            Scenario scenario = shortScenario();
            RunId abandoned = RunId.of("SIM-20261116-0009");
            repository.create(SimulationRun.start(abandoned, scenario, "admin01",
                    CLOCK.instant().minus(Duration.ofHours(3))),
                    com.example.simulationms.domain.model.valueobjects.Seed.of(0L), null);

            assertThat(useCase.run(scenario, "admin01").status())
                    .isEqualTo(RunStatus.COMPLETED);
        }

        @Test
        @DisplayName("終わった実行は二重実行にあたらない")
        void allowsANewRunAfterTheEarlierOneFinished() {
            useCase.run(shortScenario(), "admin01");

            assertThat(useCase.run(shortScenario(), "admin01").status())
                    .isEqualTo(RunStatus.COMPLETED);
        }
    }

    @Nested
    @DisplayName("実行 ID の採番")
    class Numbering {

        @Test
        @DisplayName("先を越されたら、次の番号で採り直す")
        void retriesWithTheNextNumberWhenTheIdWasTaken() {
            repository.stolen = 2;

            SimulationRun run = useCase.run(shortScenario(), "admin01");

            // 0001・0002 を先に採られ、3 度目の 0003 で通る。
            assertThat(run.runId().value()).isEqualTo("SIM-20261116-0003");
            assertThat(run.status()).isEqualTo(RunStatus.COMPLETED);
        }

        @Test
        @DisplayName("採り直しても取れ続けるなら、理由を言って止まる")
        void failsLoudlyWhenTheNumberKeepsBeingTaken() {
            repository.stolen = -1;

            Scenario scenario = shortScenario();

            assertThatThrownBy(() -> useCase.run(scenario, "admin01"))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("採番")
                    .hasMessageContaining("衝突");
        }
    }

    @Nested
    @DisplayName("継続実行が始めた 1 件")
    class FromSession {

        /**
         * <strong>乱数が選んだ入力が、業務 API まで届く</strong>（US37-1）。
         *
         * <p>生成器が選んでも、途中の層で捨てられれば「乱数で選んでいる」ことに
         * ならない——値は全層を生き延びるか確かめる。生成器だけを見るテストは、
         * 届いていなくても緑になる。
         */
        @Test
        @DisplayName("乱数が選んだ出発地・目的地・貨物種別・重量・期限が引き継がれる")
        void carriesTheGeneratedInputToTheBusinessApi() {
            ScenarioRequest request = new ScenarioRequest(shortScenario(),
                    "DEHAM", "CNSHA", "REFRIGERATED", 12_345, 99);

            useCase.runForSession(request, SessionId.of("SES-20261207-0001"), Seed.of(42L));

            Map<String, String> received = gateway.received.get(ScenarioStep.REGISTER_BOOKING);
            assertThat(received)
                    .containsEntry(BusinessContextKey.ORIGIN, "DEHAM")
                    .containsEntry(BusinessContextKey.DESTINATION, "CNSHA")
                    .containsEntry(BusinessContextKey.CARGO_TYPE, "REFRIGERATED")
                    .containsEntry(BusinessContextKey.WEIGHT_KG, "12345")
                    .containsEntry(BusinessContextKey.DEADLINE_DAYS, "99");
        }

        /** <strong>継続実行では二重実行の拒否を通さない</strong>（[ADR-031] 決定 6）。 */
        @Test
        @DisplayName("同じシナリオが実行中でも、継続実行は始められる")
        void doesNotApplyTheAlreadyRunningGuard() {
            Scenario scenario = shortScenario();
            repository.create(SimulationRun.start(RunId.of("SIM-20261116-0009"), scenario,
                    "admin01", CLOCK.instant()),
                    com.example.simulationms.domain.model.valueobjects.Seed.of(0L), null);
            ScenarioRequest request = new ScenarioRequest(scenario, "JPTYO", "USLAX",
                    "GENERAL", 900, 120);

            assertThat(useCase.runForSession(request, SessionId.of("SES-20261207-0001"),
                            Seed.of(42L)).status())
                    .isEqualTo(RunStatus.COMPLETED);
        }
    }
}
