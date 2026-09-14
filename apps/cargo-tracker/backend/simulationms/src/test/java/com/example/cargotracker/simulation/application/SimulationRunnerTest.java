package com.example.cargotracker.simulation.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.cargotracker.simulation.domain.model.aggregates.SimulationRun;
import com.example.cargotracker.simulation.domain.model.valueobjects.RunStatus;
import com.example.cargotracker.simulation.domain.model.valueobjects.Scenario;
import com.example.cargotracker.simulation.domain.model.valueobjects.StepKind;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 工程を順に実行する（US33 §受入基準 1・6 / US34 §受入基準 1・3）。
 *
 * <p><b>ここで確かめるのは段取りである。</b> どの API を叩くかは
 * {@link BusinessApi} の実装が持ち、本物は Gateway 経由の HTTP を通る
 * （[ADR-0020] 決定 2）。**段取りと経路を同じ場所に書かない**。</p>
 */
class SimulationRunnerTest {

    private static final Instant NOW = Instant.parse("2026-09-14T01:00:00Z");
    private static final Clock CLOCK = Clock.fixed(NOW, ZoneId.of("Asia/Tokyo"));

    /**
     * 工程を順に記録するだけの偽物。
     *
     * <p><b>本物より甘くしない。</b> 本物と同じく「前の工程が作った識別子」を
     * 受け取り、失敗する工程を指定できる——受け取りを捨てる偽物にすると、
     * つなぐ組み立てを潰しても緑のままになる。</p>
     */
    private static final class FakeApi implements BusinessApi {

        private final Map<StepKind, String> produces = new EnumMap<>(StepKind.class);
        private final List<StepKind> called = new ArrayList<>();
        private final List<Map<StepKind, String>> seenContext = new ArrayList<>();
        private StepKind failAt;
        private String failMessage = "落ちた";

        FakeApi produces(StepKind kind, String id) {
            produces.put(kind, id);
            return this;
        }

        FakeApi failsAt(StepKind kind, String message) {
            this.failAt = kind;
            this.failMessage = message;
            return this;
        }

        @Override
        public StepResult execute(StepKind kind, Map<StepKind, String> produced) {
            called.add(kind);
            seenContext.add(Map.copyOf(produced));
            if (kind == failAt) {
                return StepResult.failure(422, failMessage);
            }
            return StepResult.success(produces.get(kind));
        }
    }

    private static SimulationRunner runner(BusinessApi api) {
        return new SimulationRunner(api, CLOCK);
    }

    @Test
    @DisplayName("US33 §1: 工程をシナリオの順に実行し、成功で終わる")
    void runsEveryStepInOrder() {
        FakeApi api = new FakeApi();
        SimulationRun run = SimulationRun.start("run-1", Scenario.STANDARD, null,
                "admin01", NOW);

        runner(api).run(run);

        assertThat(run.status()).isEqualTo(RunStatus.SUCCEEDED);
        assertThat(api.called).isEqualTo(Scenario.STANDARD.steps());
    }

    @Test
    @DisplayName("US33 §6: 前の工程が作った識別子を、次の工程が受け取る")
    void passesProducedIdentifiersForward() {
        FakeApi api = new FakeApi()
                .produces(StepKind.REGISTER_SHIPPER, "SHP-0001")
                .produces(StepKind.REGISTER_BOOKING, "B-0001");
        SimulationRun run = SimulationRun.start("run-1", Scenario.STANDARD, null,
                "admin01", NOW);

        runner(api).run(run);

        // 3 番目の工程（経路設計への引き渡し）は、荷主と予約の識別子を見ている。
        Map<StepKind, String> atThirdStep = api.seenContext.get(2);
        assertThat(atThirdStep)
                .as("**つなぐ組み立てが潰れても緑にならない形にする**")
                .containsEntry(StepKind.REGISTER_SHIPPER, "SHP-0001")
                .containsEntry(StepKind.REGISTER_BOOKING, "B-0001");
    }

    @Test
    @DisplayName("US34 §3: 失敗した工程で止まり、以降は実行しない")
    void stopsAtTheFailedStep() {
        FakeApi api = new FakeApi().failsAt(StepKind.ASSIGN_ROUTE, "期限内に着ける便がありません");
        SimulationRun run = SimulationRun.start("run-1", Scenario.NO_ROUTE, null,
                "admin01", NOW);

        runner(api).run(run);

        assertThat(run.status()).isEqualTo(RunStatus.FAILED);
        assertThat(api.called)
                .as("以降の工程は叩かない（止まったあとに業務データを増やさない）")
                .endsWith(StepKind.ASSIGN_ROUTE);
        var failed = run.recordedSteps().get(run.recordedSteps().size() - 1);
        assertThat(failed.failureMessage()).contains("期限内に着ける便がありません");
        assertThat(failed.failureStatus()).isEqualTo(422);
    }

    @Test
    @DisplayName("US34 §1: どの工程にも所要時間が残る")
    void recordsElapsedForEveryStep() {
        SimulationRun run = SimulationRun.start("run-1", Scenario.NO_ROUTE, null,
                "admin01", NOW);

        runner(new FakeApi()).run(run);

        assertThat(run.recordedSteps())
                .isNotEmpty()
                .allSatisfy(step -> assertThat(step.elapsed()).isNotNull());
    }

    @Test
    @DisplayName("工程が例外を投げても、実行は失敗として決着する（投げっぱなしにしない）")
    void turnsAnUnexpectedErrorIntoAFailedStep() {
        BusinessApi throwing = (kind, produced) -> {
            throw new IllegalStateException("接続できません");
        };
        SimulationRun run = SimulationRun.start("run-1", Scenario.NO_ROUTE, null,
                "admin01", NOW);

        runner(throwing).run(run);

        assertThat(run.status()).isEqualTo(RunStatus.FAILED);
        assertThat(run.recordedSteps().get(0).failureMessage()).contains("接続できません");
    }
}
