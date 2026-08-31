package com.example.simulationms.infrastructure.repositories;

import com.example.simulationms.domain.model.aggregates.SimulationRun;
import com.example.simulationms.domain.model.valueobjects.RunId;
import com.example.simulationms.domain.model.valueobjects.Scenario;
import com.example.simulationms.domain.model.valueobjects.ScenarioStep;
import com.example.simulationms.domain.model.valueobjects.StepOutcome;
import com.example.simulationms.domain.model.valueobjects.StepResult;
import com.example.simulationms.domain.repository.SimulationRunRepository;
import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

/** 実行の記録を MyBatis で永続化する。 */
public class MyBatisSimulationRunRepository implements SimulationRunRepository {

    private static final String STEP_SEPARATOR = ",";

    private final SimulationRunMapper mapper;

    public MyBatisSimulationRunRepository(SimulationRunMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public void create(SimulationRun run) {
        SimulationRunRecord row = new SimulationRunRecord();
        row.setRunId(run.runId().value());
        row.setScenarioId(run.scenario().id());
        row.setSteps(run.scenario().steps().stream()
                .map(Enum::name).reduce((a, b) -> a + STEP_SEPARATOR + b).orElseThrow());
        row.setStatus(run.status().name());
        row.setStartedBy(run.startedBy());
        row.setStartedAt(run.startedAt());
        mapper.insert(row);
    }

    @Override
    public void appendResult(RunId runId, StepResult result) {
        SimulationRunRecord run = mapper.findByRunId(runId.value());
        if (run == null) {
            throw new IllegalStateException("実行が見つかりません: " + runId.value());
        }
        SimulationStepResultRecord row = new SimulationStepResultRecord();
        row.setRunId(run.getId());
        row.setStep(result.step().name());
        row.setOutcome(result.outcome().name());
        row.setElapsedMs((int) result.elapsed().toMillis());
        row.setCreatedIdentifier(result.createdIdentifier());
        row.setFailureReason(result.failureReason());
        row.setRecordedAt(result.recordedAt());
        mapper.insertResult(row);
    }

    @Override
    public int countByRunIdPrefix(String prefix) {
        return mapper.countByRunIdPrefix(prefix);
    }

    @Override
    public Optional<SimulationRun> findByRunId(RunId runId) {
        return Optional.ofNullable(mapper.findByRunId(runId.value())).map(this::toDomain);
    }

    @Override
    public List<SimulationRun> findRecent(int limit) {
        return mapper.findRecent(limit).stream().map(this::toDomain).toList();
    }

    @Override
    public Optional<SimulationRun> findRunningByScenario(String scenarioId) {
        // 工程数はシナリオの定義から取る。実行中かどうかは「失敗が無く、全工程を
        // 終えていない」ことで決まるため、比較の相手が要る
        Scenario scenario = scenarioOf(scenarioId);
        return Optional.ofNullable(
                        mapper.findRunningByScenario(scenarioId, scenario.steps().size()))
                .map(this::toDomain);
    }

    /**
     * シナリオ ID から定義を引く。
     *
     * <p>実行中の判定にしか使わない。<strong>復元には使わない</strong>——復元は
     * 行に残した並びを読む。定義を変えたあとに過去の実行を読んでも、当時の並びで復元される。
     */
    private Scenario scenarioOf(String scenarioId) {
        SimulationRunRecord latest = mapper.findRecent(Integer.MAX_VALUE).stream()
                .filter(row -> row.getScenarioId().equals(scenarioId))
                .findFirst()
                .orElse(null);
        return latest == null ? Scenario.standardTransport() : toScenario(latest);
    }

    private SimulationRun toDomain(SimulationRunRecord row) {
        List<StepResult> results = mapper.findResults(row.getId()).stream()
                .map(MyBatisSimulationRunRepository::toStepResult)
                .toList();
        return SimulationRun.restore(RunId.of(row.getRunId()), toScenario(row),
                row.getStartedBy(), row.getStartedAt(), results);
    }

    private static Scenario toScenario(SimulationRunRecord row) {
        return Scenario.of(row.getScenarioId(),
                Arrays.stream(row.getSteps().split(STEP_SEPARATOR))
                        .map(ScenarioStep::valueOf)
                        .toList());
    }

    private static StepResult toStepResult(SimulationStepResultRecord row) {
        return new StepResult(ScenarioStep.valueOf(row.getStep()),
                StepOutcome.valueOf(row.getOutcome()),
                Duration.ofMillis(row.getElapsedMs()),
                row.getCreatedIdentifier(), row.getFailureReason(),
                row.getRecordedAt());
    }
}
