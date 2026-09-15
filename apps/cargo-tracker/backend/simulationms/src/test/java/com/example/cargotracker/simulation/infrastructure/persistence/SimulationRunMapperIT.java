package com.example.cargotracker.simulation.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.cargotracker.shared.testing.AbstractAxonIntegrationTest;
import java.time.Instant;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.test.annotation.DirtiesContext;

/**
 * 実行の記録（US33 §受入基準 5 / US34 §受入基準 4）。
 *
 * <p><b>二重実行は DB が断る。</b> アプリケーション層で数えてから入れる形は、
 * 2 つの要求が同時に来たときに両方とも通る——部分ユニークで守る。</p>
 *
 * <p><b>記録の側も検査する。</b> 書いて、読んで、期待値と比べるまでを 1 本で通す。</p>
 */
@SpringBootTest
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class SimulationRunMapperIT extends AbstractAxonIntegrationTest {

    private static final Instant AT = Instant.parse("2026-09-14T01:00:00Z");

    @Autowired
    private SimulationRunMapper runs;

    private static SimulationRunMapper.RunRow running(String runId, String scenarioId) {
        return new SimulationRunMapper.RunRow(runId, scenarioId, "RUNNING", null,
                AT, null, "admin01", AT);
    }

    @Test
    @DisplayName("US34 §4: 実行と工程を書いて、読み直せる")
    void writesAndReadsTheRun() {
        String runId = "run-" + System.nanoTime();
        runs.insert(running(runId, "SC-" + System.nanoTime()));
        runs.insertStep(new SimulationRunMapper.StepRow(runId, 1, "REGISTER_SHIPPER",
                "SUCCEEDED", 120L, "SHP-0001", null, null, AT, 3000L));

        assertThat(runs.find(runId)).isNotNull()
                .satisfies(row -> assertThat(row.status()).isEqualTo("RUNNING"))
                .satisfies(row -> assertThat(row.startedBy()).isEqualTo("admin01"));
        assertThat(runs.findSteps(runId)).hasSize(1)
                .first()
                .satisfies(step -> assertThat(step.producedId()).isEqualTo("SHP-0001"))
                .satisfies(step -> assertThat(step.elapsedMs()).isEqualTo(120L));
    }

    @Test
    @DisplayName("US33 §5: 同じシナリオは実行中のものが 1 本だけ（DB が断る）")
    void refusesASecondRunningRunOfTheSameScenario() {
        String scenarioId = "SC-" + System.nanoTime();
        runs.insert(running("run-a-" + System.nanoTime(), scenarioId));

        assertThatThrownBy(() -> runs.insert(running("run-b-" + System.nanoTime(), scenarioId)))
                .as("**数えてから入れる形にしない。** 同時に来た 2 つが両方とも通る")
                .isInstanceOf(DuplicateKeyException.class);
    }

    @Test
    @DisplayName("終わった実行があっても、同じシナリオをもう一度流せる")
    void allowsANewRunAfterThePreviousOneFinished() {
        String scenarioId = "SC-" + System.nanoTime();
        String first = "run-a-" + System.nanoTime();
        runs.insert(running(first, scenarioId));
        runs.updateStatus(first, "SUCCEEDED", AT, AT);

        // **部分ユニークは「実行中」だけを見る。** 全体に掛けると、1 度流した
        // シナリオを二度と流せなくなる。
        runs.insert(running("run-b-" + System.nanoTime(), scenarioId));

        assertThat(runs.findRunning(scenarioId)).isNotNull();
    }

    @Test
    @DisplayName("断りに実行中の識別子を添えられる（いまの結果へ案内する）")
    void findsTheRunningRunToPointAt() {
        String scenarioId = "SC-" + System.nanoTime();
        String runId = "run-" + System.nanoTime();
        runs.insert(running(runId, scenarioId));

        assertThat(runs.findRunning(scenarioId)).isNotNull()
                .satisfies(row -> assertThat(row.runId()).isEqualTo(runId));
    }
}
