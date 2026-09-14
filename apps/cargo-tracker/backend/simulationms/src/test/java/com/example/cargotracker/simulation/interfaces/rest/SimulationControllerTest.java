package com.example.cargotracker.simulation.interfaces.rest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.cargotracker.shared.domain.error.BusinessRuleViolation;
import com.example.cargotracker.simulation.application.BusinessApi;
import com.example.cargotracker.simulation.application.SimulationRunner;
import com.example.cargotracker.simulation.application.SimulationService;
import com.example.cargotracker.simulation.domain.model.valueobjects.Scenario;
import com.example.cargotracker.simulation.infrastructure.config.SimulationProperties;
import com.example.cargotracker.simulation.infrastructure.persistence.SimulationRunMapper;
import com.example.cargotracker.simulation.infrastructure.query.SimulationQueries;
import com.example.cargotracker.simulation.infrastructure.query.SimulationQueryHandler;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

/** 業務シミュレーションの入口（S92・S93 / US33・US34）。 */
class SimulationControllerTest {

    private static final Clock CLOCK =
            Clock.fixed(Instant.parse("2026-09-14T01:00:00Z"), ZoneOffset.UTC);

    private final List<SimulationRunMapper.RunRow> rows = new ArrayList<>();
    private final List<SimulationRunMapper.StepRow> steps = new ArrayList<>();

    private final SimulationRunMapper runs = new SimulationRunMapper() {

        @Override
        public int insert(RunRow row) {
            rows.add(row);
            return 1;
        }

        @Override
        public int updateStatus(String runId, String status, Instant finishedAt,
                Instant projectedAt) {
            return 1;
        }

        @Override
        public int insertStep(StepRow row) {
            steps.add(row);
            return 1;
        }

        @Override
        public RunRow find(String runId) {
            return rows.stream().filter(r -> r.runId().equals(runId)).findFirst()
                    .orElse(null);
        }

        @Override
        public List<RunRow> findRecent(int limit) {
            return rows;
        }

        @Override
        public RunRow findRunning(String scenarioId) {
            return null;
        }

        @Override
        public List<StepRow> findSteps(String runId) {
            return steps;
        }
    };

    private SimulationController controller(boolean enabled) {
        SimulationService service = new SimulationService(runs,
                new SimulationProperties(enabled, "http://gw"),
                (scenario, listener) -> new SimulationRunner(
                        (kind, produced) -> BusinessApi.StepResult.success(null),
                        (kind, produced) -> true, duration -> { }, CLOCK, listener),
                Runnable::run, CLOCK);
        return new SimulationController(new SimulationQueryHandler(runs), service);
    }

    @Test
    @DisplayName("実行を始めると識別子を返す（画面はその結果へ移る）")
    void returnsTheRunId() {
        var response = controller(true).start("admin01",
                new SimulationController.StartRequest(Scenario.NO_ROUTE.label()));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody().runId()).startsWith("SIM-");
        assertThat(response.getHeaders().getLocation().toString())
                .endsWith(response.getBody().runId());
    }

    @Test
    @DisplayName("知らないシナリオは断る（打ち間違いを「工程 0 件で成功」にしない）")
    void refusesUnknownScenario() {
        assertThatThrownBy(() -> controller(true).start("admin01",
                new SimulationController.StartRequest("そんなシナリオは無い")))
                .isInstanceOf(BusinessRuleViolation.class)
                .hasMessageContaining("知らないシナリオ");
    }

    @Test
    @DisplayName("US33 §4: 無効な環境では断る")
    void refusesWhenDisabled() {
        assertThatThrownBy(() -> controller(false).start("admin01",
                new SimulationController.StartRequest(Scenario.STANDARD.label())))
                .isInstanceOf(BusinessRuleViolation.class);
    }

    @Test
    @DisplayName("知らない実行は「見つかりません」（空の結果を返さない）")
    void returnsNotFoundForUnknownRun() {
        assertThat(controller(true).run("SIM-unknown").getStatusCode())
                .isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    @DisplayName("実行の一覧と詳細が読める（S92・S93）")
    void readsRuns() {
        SimulationController controller = controller(true);
        String runId = controller.start("admin01",
                new SimulationController.StartRequest(Scenario.NO_ROUTE.label()))
                .getBody().runId();

        SimulationQueries.RunListView list = controller.recent().getBody();
        assertThat(list.items()).singleElement().satisfies(item -> {
            assertThat(item.runId()).isEqualTo(runId);
            assertThat(item.scenarioLabel()).isEqualTo(Scenario.NO_ROUTE.label());
            // **どこまで進んだかが一覧から読める**（開かないと分からない形にしない）。
            assertThat(item.plannedSteps()).isEqualTo(Scenario.NO_ROUTE.steps().size());
            assertThat(item.succeededSteps()).isEqualTo(Scenario.NO_ROUTE.steps().size());
        });

        SimulationQueries.RunView view = controller.run(runId).getBody();
        assertThat(view.startedBy()).isEqualTo("admin01");
        assertThat(view.steps()).hasSize(Scenario.NO_ROUTE.steps().size());
    }
}
