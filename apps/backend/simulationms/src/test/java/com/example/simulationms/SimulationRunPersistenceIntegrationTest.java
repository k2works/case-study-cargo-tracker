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

    private static Scenario shortScenario() {
        return Scenario.of("short-transport",
                List.of(ScenarioStep.REGISTER_SHIPPER, ScenarioStep.REGISTER_BOOKING));
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

        assertThat(runs.findRunningByScenario("short-transport")).isPresent();
        assertThat(runs.findRunningByScenario("standard-transport")).isEmpty();
    }

    @Test
    @DisplayName("終わった実行は、二重実行の判定に現れない")
    void doesNotReportFinishedRunAsRunning() {
        create("SIM-20261116-0004");
        RunId id = RunId.of("SIM-20261116-0004");
        runs.appendResult(id, StepResult.failed(ScenarioStep.REGISTER_SHIPPER,
                Duration.ofMillis(10), "接続できません", Instant.parse("2026-11-16T01:00:01Z")));

        assertThat(runs.findRunningByScenario("short-transport"))
                .map(run -> run.runId().value())
                .isNotEqualTo(java.util.Optional.of("SIM-20261116-0004"));
    }

    @Test
    @DisplayName("一覧は新しい順に返り、上限で切る")
    void listsRecentRuns() {
        create("SIM-20261116-0005");
        create("SIM-20261116-0006");

        List<SimulationRun> recent = runs.findRecent(1);

        assertThat(recent).hasSize(1);
    }
}
