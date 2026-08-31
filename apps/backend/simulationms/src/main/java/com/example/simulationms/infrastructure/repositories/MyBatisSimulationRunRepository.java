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
        SimulationRunRecord record = new SimulationRunRecord();
        record.setRunId(run.runId().value());
        record.setScenarioId(run.scenario().id());
        record.setSteps(run.scenario().steps().stream()
                .map(Enum::name).reduce((a, b) -> a + STEP_SEPARATOR + b).orElseThrow());
        record.setStatus(run.status().name());
        record.setStartedBy(run.startedBy());
        record.setStartedAt(run.startedAt());
        mapper.insert(record);
    }

    @Override
    public void appendResult(RunId runId, StepResult result) {
        SimulationRunRecord run = mapper.findByRunId(runId.value());
        if (run == null) {
            throw new IllegalStateException("実行が見つかりません: " + runId.value());
        }
        SimulationStepResultRecord record = new SimulationStepResultRecord();
        record.setRunId(run.getId());
        record.setStep(result.step().name());
        record.setOutcome(result.outcome().name());
        record.setElapsedMs((int) result.elapsed().toMillis());
        record.setCreatedIdentifier(result.createdIdentifier());
        record.setFailureReason(result.failureReason());
        record.setRecordedAt(result.recordedAt());
        mapper.insertResult(record);
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
                .filter(record -> record.getScenarioId().equals(scenarioId))
                .findFirst()
                .orElse(null);
        return latest == null ? Scenario.standardTransport() : toScenario(latest);
    }

    private SimulationRun toDomain(SimulationRunRecord record) {
        List<StepResult> results = mapper.findResults(record.getId()).stream()
                .map(MyBatisSimulationRunRepository::toStepResult)
                .toList();
        return SimulationRun.restore(RunId.of(record.getRunId()), toScenario(record),
                record.getStartedBy(), record.getStartedAt(), results);
    }

    private static Scenario toScenario(SimulationRunRecord record) {
        return Scenario.of(record.getScenarioId(),
                Arrays.stream(record.getSteps().split(STEP_SEPARATOR))
                        .map(ScenarioStep::valueOf)
                        .toList());
    }

    private static StepResult toStepResult(SimulationStepResultRecord record) {
        return new StepResult(ScenarioStep.valueOf(record.getStep()),
                StepOutcome.valueOf(record.getOutcome()),
                Duration.ofMillis(record.getElapsedMs()),
                record.getCreatedIdentifier(), record.getFailureReason(),
                record.getRecordedAt());
    }
}
