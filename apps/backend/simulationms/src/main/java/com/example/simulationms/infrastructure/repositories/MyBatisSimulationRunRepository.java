package com.example.simulationms.infrastructure.repositories;

import com.example.simulationms.domain.model.aggregates.SimulationRun;
import com.example.simulationms.domain.model.valueobjects.RunId;
import com.example.simulationms.domain.model.valueobjects.Scenario;
import com.example.simulationms.domain.model.valueobjects.ScenarioStep;
import com.example.simulationms.domain.model.valueobjects.StepOutcome;
import com.example.simulationms.domain.model.valueobjects.StepResult;
import com.example.simulationms.domain.repository.RunIdAlreadyTakenException;
import com.example.simulationms.domain.repository.SimulationRunRepository;
import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import org.springframework.dao.DuplicateKeyException;

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
        try {
            mapper.insert(row);
        } catch (DuplicateKeyException e) {
            // 同時に始まった別の実行が、同じ番号を先に採っていた。
            // 呼ぶ側が次の番号で採り直せるように、永続化の例外をドメインの言葉に変える。
            throw new RunIdAlreadyTakenException(run.runId().value(), e);
        }
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
    public Optional<SimulationRun> findRunningByScenario(Scenario scenario,
            java.time.Instant staleBefore) {
        return Optional.ofNullable(mapper.findRunningByScenario(
                        scenario.id(), scenario.steps().size(), staleBefore))
                .map(this::toDomain);
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
