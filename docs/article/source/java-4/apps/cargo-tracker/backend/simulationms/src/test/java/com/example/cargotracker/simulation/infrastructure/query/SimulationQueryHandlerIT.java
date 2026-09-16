package com.example.cargotracker.simulation.infrastructure.query;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.cargotracker.shared.testing.AbstractAxonIntegrationTest;
import com.example.cargotracker.simulation.infrastructure.persistence.SimulationRunMapper;
import java.time.Instant;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;

/**
 * 実行結果の読み口（US34 §受入基準 1・2・4・5）。
 *
 * <p><b>列挙名を出さない。</b> 読む人は業務の言葉で読む——`REGISTER_SHIPPER` では
 * なく「荷主の登録」、`FAILED` ではなく「失敗」。</p>
 *
 * <p><b>生成した識別子をそのまま返す。</b> 業務画面へ行けることが §受入基準 5 で、
 * 画面は識別子からリンクを組み立てる。</p>
 */
@SpringBootTest
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class SimulationQueryHandlerIT extends AbstractAxonIntegrationTest {

    private static final Instant AT = Instant.parse("2026-09-14T01:00:00Z");

    @Autowired
    private SimulationRunMapper runs;

    @Autowired
    private SimulationQueryHandler queries;

    private String seedRun(String status) {
        String runId = "run-" + System.nanoTime();
        runs.insert(new SimulationRunMapper.RunRow(runId, "STANDARD", status, null,
                AT, "SUCCEEDED".equals(status) ? AT : null, "admin01", AT, null));
        runs.insertStep(new SimulationRunMapper.StepRow(runId, 1, "REGISTER_SHIPPER",
                "SUCCEEDED", 120L, "SHP-0001", null, null, AT, 3000L));
        runs.insertStep(new SimulationRunMapper.StepRow(runId, 2, "REGISTER_BOOKING",
                "FAILED", 45L, null, 422, "出発地と目的地が同じです", AT, 0L));
        return runId;
    }

    @Test
    @DisplayName("US34 §1: 工程ごとに成否・所要時間・生成した識別子が読める")
    void readsEachStepWithOutcomeElapsedAndProducedId() {
        String runId = seedRun("FAILED");

        var view = queries.findRun(runId);

        assertThat(view).isNotNull();
        assertThat(view.steps()).hasSize(2);
        var first = view.steps().get(0);
        assertThat(first.kindLabel())
                .as("**列挙名を出さない。** 読む人は業務の言葉で読む")
                .isEqualTo("荷主の登録");
        assertThat(first.outcomeLabel()).isEqualTo("成功");
        assertThat(first.elapsedMs()).isEqualTo(120L);
        assertThat(first.producedId())
                .as("**ここから業務画面へ行ける**（§5）")
                .isEqualTo("SHP-0001");
    }

    @Test
    @DisplayName("US34 §2: 失敗した工程には理由（応答コードとメッセージ）が出る")
    void showsWhyTheStepFailed() {
        String runId = seedRun("FAILED");

        var failed = queries.findRun(runId).steps().get(1);

        assertThat(failed.outcomeLabel()).isEqualTo("失敗");
        assertThat(failed.failureStatus()).isEqualTo(422);
        assertThat(failed.failureMessage())
                .as("**「失敗しました」では切り分けられない。**")
                .contains("出発地と目的地が同じ");
    }

    @Test
    @DisplayName("US34 §4: 実行の一覧から過去の実行を開ける（新しい順）")
    void listsRecentRunsNewestFirst() {
        String older = seedRun("SUCCEEDED");
        String newer = "run-" + System.nanoTime();
        runs.insert(new SimulationRunMapper.RunRow(newer, "STANDARD", "RUNNING", null,
                AT.plusSeconds(60), null, "admin01", AT, null));

        var ids = queries.findRecentRuns(50).items().stream()
                .map(SimulationQueries.RunSummaryView::runId)
                .toList();

        assertThat(ids).contains(older, newer);
        assertThat(ids.indexOf(newer))
                .as("**新しい順。** 見たいのはいま流したものである")
                .isLessThan(ids.indexOf(older));
    }

    @Test
    @DisplayName("実行の状態も業務の言葉で読める")
    void showsTheRunStatusInBusinessWords() {
        String runId = seedRun("FAILED");

        assertThat(queries.findRun(runId).statusLabel()).isEqualTo("失敗");
    }

    @Test
    @DisplayName("知らない実行は null（画面が「見つかりません」を出せる）")
    void returnsNullForAnUnknownRun() {
        assertThat(queries.findRun("run-" + System.nanoTime())).isNull();
    }
}
