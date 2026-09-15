package com.example.cargotracker.simulation.infrastructure.query;

import com.example.cargotracker.simulation.domain.model.valueobjects.RunStatus;
import com.example.cargotracker.simulation.domain.model.valueobjects.Scenario;
import com.example.cargotracker.simulation.domain.model.valueobjects.StepKind;
import com.example.cargotracker.simulation.domain.model.valueobjects.StepOutcome;
import com.example.cargotracker.simulation.infrastructure.persistence.SimulationRunMapper;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * 実行結果を読む（US34）。
 *
 * <p><b>呼び名は列挙が持つ。</b> ここで書き直すと、画面ごとに違う呼び名が出る
 * （呼び名の出典は 1 つ）。</p>
 */
@Component
public class SimulationQueryHandler {

    private final SimulationRunMapper runs;

    public SimulationQueryHandler(SimulationRunMapper runs) {
        this.runs = runs;
    }

    /** 実行の一覧（S92）。<b>新しい順</b>——見たいのはいま流したものである。 */
    public SimulationQueries.RunListView findRecentRuns(int limit) {
        return new SimulationQueries.RunListView(runs.findRecent(limit).stream()
                .map(this::toSummary)
                .toList());
    }

    /** 実行の詳細（S93）。<b>知らない実行は {@code null}</b>——画面が「見つかりません」を出す。 */
    public SimulationQueries.RunView findRun(String runId) {
        SimulationRunMapper.RunRow row = runs.find(runId);
        if (row == null) {
            return null;
        }
        Scenario scenario = Scenario.valueOf(row.scenarioId());
        RunStatus status = RunStatus.valueOf(row.status());
        List<SimulationQueries.StepView> steps = runs.findSteps(runId).stream()
                .map(SimulationQueryHandler::toStep)
                .toList();
        // **予定の工程はシナリオが持つ。** 画面に持たせると、列挙に値を足した
        // ときに片方だけが古くなる（IT16 のレビュー N4）。
        List<SimulationQueries.PlannedStepView> planned = java.util.stream.IntStream
                .range(0, scenario.steps().size())
                .mapToObj(index -> new SimulationQueries.PlannedStepView(index + 1,
                        scenario.steps().get(index).name(),
                        scenario.steps().get(index).label()))
                .toList();
        return new SimulationQueries.RunView(row.runId(), scenario.name(), scenario.label(),
                status.name(), status.label(), row.seed(), row.startedAt(), row.finishedAt(),
                row.startedBy(), steps, planned);
    }

    private SimulationQueries.RunSummaryView toSummary(SimulationRunMapper.RunRow row) {
        Scenario scenario = Scenario.valueOf(row.scenarioId());
        RunStatus status = RunStatus.valueOf(row.status());
        // **どこまで進んだかを一覧から読める。** 開かないと分からない形にしない。
        int succeeded = (int) runs.findSteps(row.runId()).stream()
                .filter(step -> StepOutcome.SUCCEEDED.name().equals(step.outcome()))
                .count();
        return new SimulationQueries.RunSummaryView(row.runId(), scenario.label(),
                status.name(), status.label(), row.startedAt(), row.finishedAt(),
                row.startedBy(), succeeded, scenario.steps().size());
    }

    private static SimulationQueries.StepView toStep(SimulationRunMapper.StepRow row) {
        StepKind kind = StepKind.valueOf(row.kind());
        StepOutcome outcome = StepOutcome.valueOf(row.outcome());
        return new SimulationQueries.StepView(row.stepNo(), kind.name(), kind.label(),
                outcome.name(), outcome.label(), row.elapsedMs(), row.producedId(),
                row.failureStatus(), row.failureMessage(), row.occurredAt(), row.waitedMs());
    }
}
