package com.example.simulationms.application.internal.queryservices;

import com.example.simulationms.domain.model.aggregates.SimulationRun;
import com.example.simulationms.domain.model.valueobjects.RunStatus;
import com.example.simulationms.domain.model.valueobjects.ScenarioStep;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 実行の統計（US37-8）。
 *
 * <p><strong>失敗した工程の分布が肝である。</strong>件数だけでは「たくさん落ちている」
 * としか分からない。どの工程で落ちているかが分かって初めて、直す場所が決まる。
 * ——気づく手段は次の行動へ繋ぐ。
 *
 * <p>状態は<strong>工程の結果から導く</strong>（[ADR-030] 決定 5）。列に持たせて
 * 数えると、二重に持った片方だけが更新された行を数えることになる。
 *
 * @param total 実行の件数
 * @param succeeded 最後まで通った件数
 * @param failed 途中で止まった件数
 * @param running まだ動いている件数
 * @param failuresByStep 失敗した工程ごとの件数（多い順）
 */
public record SimulationStatistics(int total, int succeeded, int failed, int running,
        Map<ScenarioStep, Integer> failuresByStep) {

    public static SimulationStatistics of(List<SimulationRun> runs) {
        int succeeded = (int) runs.stream()
                .filter(run -> run.status() == RunStatus.COMPLETED).count();
        int failed = (int) runs.stream()
                .filter(run -> run.status() == RunStatus.FAILED).count();
        int running = (int) runs.stream()
                .filter(run -> run.status() == RunStatus.RUNNING).count();
        return new SimulationStatistics(runs.size(), succeeded, failed, running,
                failuresByStep(runs));
    }

    /**
     * 失敗した工程を多い順に数える。
     *
     * <p>読む人が並べ替えなくてよい形にする——直す場所を決める材料である。
     */
    private static Map<ScenarioStep, Integer> failuresByStep(List<SimulationRun> runs) {
        Map<ScenarioStep, Integer> counts = new java.util.EnumMap<>(ScenarioStep.class);
        for (SimulationRun run : runs) {
            run.results().stream()
                    .filter(com.example.simulationms.domain.model.valueobjects.StepResult::failed)
                    .forEach(result -> counts.merge(result.step(), 1, Integer::sum));
        }
        Map<ScenarioStep, Integer> ordered = new LinkedHashMap<>();
        counts.entrySet().stream()
                .sorted(Comparator.<Map.Entry<ScenarioStep, Integer>>comparingInt(
                                Map.Entry::getValue).reversed()
                        .thenComparing(entry -> entry.getKey().name()))
                .forEach(entry -> ordered.put(entry.getKey(), entry.getValue()));
        return Map.copyOf(ordered).isEmpty() ? Map.of() : java.util.Collections
                .unmodifiableMap(ordered);
    }
}
