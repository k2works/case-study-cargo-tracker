package com.example.simulationms;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.simulationms.domain.model.aggregates.SimulationRun;
import com.example.simulationms.domain.model.valueobjects.RunId;
import com.example.simulationms.domain.model.valueobjects.RunStatus;
import com.example.simulationms.domain.model.valueobjects.Scenario;
import com.example.simulationms.domain.model.valueobjects.ScenarioStep;
import com.example.simulationms.domain.model.valueobjects.StepResult;
import com.example.simulationms.domain.repository.SimulationRunRepository;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * 実行の永続化（US34・US35）。
 *
 * <p><strong>実 DB で確かめる。</strong>列名の誤りも、外部キーの抜けも、スタブでは
 * 見つからない。
 *
 * <p><strong>失敗しても巻き戻さない</strong>（[ADR-030] 決定 5）ことを、実際に
 * 失敗の結果を書いたうえで、それまでの工程が読めることで確かめる。
 */
@SpringBootTest
@Testcontainers
@ExtendWith(SpringExtension.class)
@ActiveProfiles("integration")
@DisplayName("実行の永続化")
class SimulationRunPersistenceIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired
    private SimulationRunRepository runs;

    private static final Instant STARTED = Instant.parse("2026-11-16T01:00:00Z");

    /** 見切りの境目より十分に前。これを渡せば「止まったきり」の判定は働かない。 */
    private static final Instant LONG_AGO = Instant.parse("2020-01-01T00:00:00Z");

    private static Scenario shortScenario() {
        return scenario("short-transport");
    }

    /**
     * テストごとにシナリオを分ける。
     *
     * <p>DB を共有しているため、同じシナリオ名を使うと<strong>他のテストが作った実行</strong>が
     * 実行中として引かれる。名前を分けないと、判定の対象が入れ替わっていることに気づけない。
     */
    private static Scenario scenario(String id) {
        return Scenario.of(id,
                List.of(ScenarioStep.REGISTER_SHIPPER, ScenarioStep.REGISTER_BOOKING));
    }

    private SimulationRun create(String runId, Scenario scenario) {
        SimulationRun run = SimulationRun.start(RunId.of(runId), scenario, "admin01", STARTED);
        runs.create(run);
        return run;
    }

    private SimulationRun create(String runId) {
        SimulationRun run = SimulationRun.start(RunId.of(runId), shortScenario(),
                "admin01", STARTED);
        runs.create(run);
        return run;
    }

    @Test
    @DisplayName("開始した実行を、工程の結果ごと読み戻せる")
    void savesAndRestoresRun() {
        create("SIM-20261116-0001");
        runs.appendResult(RunId.of("SIM-20261116-0001"), StepResult.succeeded(
                ScenarioStep.REGISTER_SHIPPER, Duration.ofMillis(120), "SIM-0001",
                Instant.parse("2026-11-16T01:00:01Z")));

        SimulationRun restored = runs.findByRunId(RunId.of("SIM-20261116-0001")).orElseThrow();

        assertThat(restored.startedBy()).isEqualTo("admin01");
        assertThat(restored.scenario().id()).isEqualTo("short-transport");
        assertThat(restored.status()).isEqualTo(RunStatus.RUNNING);
        assertThat(restored.identifierOf(ScenarioStep.REGISTER_SHIPPER)).contains("SIM-0001");
    }

    @Test
    @DisplayName("失敗した工程を書いても、それまでの業務データの記録は残る")
    void keepsEarlierResultsWhenAStepFails() {
        create("SIM-20261116-0002");
        RunId id = RunId.of("SIM-20261116-0002");
        runs.appendResult(id, StepResult.succeeded(ScenarioStep.REGISTER_SHIPPER,
                Duration.ofMillis(120), "SIM-0002", Instant.parse("2026-11-16T01:00:01Z")));
        runs.appendResult(id, StepResult.failed(ScenarioStep.REGISTER_BOOKING,
                Duration.ofMillis(90), "500 予約の登録に失敗しました",
                Instant.parse("2026-11-16T01:00:02Z")));

        SimulationRun restored = runs.findByRunId(id).orElseThrow();

        assertThat(restored.status()).isEqualTo(RunStatus.FAILED);
        assertThat(restored.identifierOf(ScenarioStep.REGISTER_SHIPPER)).contains("SIM-0002");
        assertThat(restored.failureReason()).contains("500 予約の登録に失敗しました");
        assertThat(restored.reachedStep()).contains(ScenarioStep.REGISTER_BOOKING);
    }

    @Test
    @DisplayName("実行中のシナリオは、二重実行を断る根拠として引ける")
    void findsRunningRunByScenario() {
        create("SIM-20261116-0003");

        assertThat(runs.findRunningByScenario(shortScenario(), LONG_AGO)).isPresent();
        assertThat(runs.findRunningByScenario(Scenario.standardTransport(), LONG_AGO)).isEmpty();
    }

    /**
     * <strong>止まったきりの実行は、実行中とみなさない。</strong>
     *
     * <p>残すと、そのシナリオは二度と実行できなくなり、復旧手段が DB を手で触ることしか
     * 無くなる（Pod の再起動・配備で普通に起きる）。
     */
    @Test
    @DisplayName("長く音沙汰の無い実行は、実行中として引かれない")
    void doesNotReportAStaleRunAsRunning() {
        Scenario scenario = scenario("stale-only");
        create("SIM-20261116-0007", scenario);

        assertThat(runs.findRunningByScenario(scenario,
                STARTED.plus(Duration.ofHours(1))))
                .as("見切らないと、そのシナリオは二度と実行できない")
                .isEmpty();
    }

    @Test
    @DisplayName("終わった実行は、二重実行の判定に現れない")
    void doesNotReportFinishedRunAsRunning() {
        Scenario scenario = scenario("finished-only");
        create("SIM-20261116-0004", scenario);
        RunId id = RunId.of("SIM-20261116-0004");
        runs.appendResult(id, StepResult.failed(ScenarioStep.REGISTER_SHIPPER,
                Duration.ofMillis(10), "接続できません", Instant.parse("2026-11-16T01:00:01Z")));

        assertThat(runs.findRunningByScenario(scenario, LONG_AGO))
                .as("失敗した実行が実行中として引かれると、次の実行が始められない")
                .isEmpty();
    }

    @Test
    @DisplayName("一覧は新しい順に返り、上限で切る")
    void listsRecentRuns() {
        create("SIM-20261116-0005");
        create("SIM-20261116-0006");

        List<SimulationRun> recent = runs.findRecent(1);

        assertThat(recent).hasSize(1);
        // **新しい順であることまで見る。**件数だけでは、古い順に壊しても緑になる
        assertThat(recent.getFirst().runId().value()).isEqualTo("SIM-20261116-0006");
    }
}
