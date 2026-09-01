package com.example.simulationms.application.internal.queryservices;

import com.example.simulationms.domain.model.aggregates.SimulationRun;
import com.example.simulationms.domain.model.valueobjects.RunStatus;
import com.example.simulationms.domain.model.valueobjects.ScenarioStep;
import java.time.Instant;
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
 * @param abandoned 止まったきりの件数
 * @param failuresByStep 失敗した工程ごとの件数（多い順）
 */
public record SimulationStatistics(int total, int succeeded, int failed, int running,
        int abandoned, Map<ScenarioStep, Integer> failuresByStep) {

    /**
     * 実行の一覧から数える。
     *
     * <p><strong>止まったきりを「実行中」に数えない</strong>（IT15 のレビュー指摘）。
     * 配備や Pod の再起動で途中終了した実行は、工程の結果から導くと永久に
     * 「実行中」で残る——「実行中 7 件」と出ていると、管理者は止めてよいのか
     * まだ待つのかを判断できない。
     *
     * <p>見切りの判定は {@link SimulationRun#abandoned} に 1 つ置く。
     *
     * @param staleBefore この時刻より古い記録しか持たない実行中は、中断とみなす
     */
    public static SimulationStatistics of(List<SimulationRun> runs, Instant staleBefore) {
        int succeeded = (int) runs.stream()
                .filter(run -> run.status() == RunStatus.COMPLETED).count();
        int failed = (int) runs.stream()
                .filter(run -> run.status() == RunStatus.FAILED).count();
        int abandoned = (int) runs.stream().filter(run -> run.abandoned(staleBefore)).count();
        int running = (int) runs.stream()
                .filter(run -> run.status() == RunStatus.RUNNING).count() - abandoned;
        return new SimulationStatistics(runs.size(), succeeded, failed, running, abandoned,
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
