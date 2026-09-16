package com.example.cargotracker.simulation.infrastructure.query;

import java.time.Instant;
import java.util.List;

/**
 * 実行結果の読み口（S92・S93 / US34）。
 *
 * <p><b>列挙名を出さない。</b> 読む人は業務の言葉で読む。呼び名の出典は列挙が
 * 1 つ持ち、ここで書き直さない。</p>
 */
public final class SimulationQueries {

    private SimulationQueries() {
    }

    /** 実行の一覧 1 行（S92）。 */
    public record RunSummaryView(
            String runId,
            String scenarioLabel,
            String status,
            String statusLabel,
            Instant startedAt,
            Instant finishedAt,
            String startedBy,
            // 記録した工程のうち成功した数 / 予定の工程数。**どこまで進んだか**が
            // 一覧から読める（開かないと分からない形にしない）。
            int succeededSteps,
            int plannedSteps) {
    }

    /** 実行の一覧（S92）。 */
    public record RunListView(List<RunSummaryView> items) {
    }

    /**
     * 工程 1 件（S93 / US34 §受入基準 1・2・5）。
     *
     * @param producedId その工程が生成した識別子。<b>画面はここからリンクを組み立てる</b>
     */
    public record StepView(
            int stepNo,
            String kind,
            String kindLabel,
            String outcome,
            String outcomeLabel,
            Long elapsedMs,
            String producedId,
            Integer failureStatus,
            String failureMessage,
            Instant occurredAt,
            // 連鎖の結果を待った時間（IT16 のレビュー N8）。**所要時間と分けて出す**
            // ——足し合わせると「13 工程が数ミリ秒ずつ」と読めてしまい、実際に
            // 時間を使っている場所が見えない。
            Long waitedMs) {
    }

    /** 実行の詳細（S93）。 */
    public record RunView(
            String runId,
            String scenario,
            String scenarioLabel,
            String status,
            String statusLabel,
            Long seed,
            Instant startedAt,
            Instant finishedAt,
            String startedBy,
            List<StepView> steps,
            /*
             * 予定の工程（IT16 のレビュー N4）。
             *
             * <p><b>記録済みの工程だけでは「進んでいるのか固まったのか」が
             * 分からない。</b> 連鎖待ちは 1 工程あたり最大 30 秒あり、そのあいだ
             * 画面には何も増えない。予定を並べておけば、どこで止まっているかが
             * 一目で読める。</p>
             *
             * <p><b>件数ではなく中身を渡す。</b> 画面が工程の呼び名を持つと、
             * 列挙に値を足したときに片方だけが古くなる。</p>
             */
            List<PlannedStepView> plannedSteps) {
    }

    /** 予定の工程 1 件（S93 / IT16 のレビュー N4）。 */
    public record PlannedStepView(int stepNo, String kind, String kindLabel) {
    }
}
