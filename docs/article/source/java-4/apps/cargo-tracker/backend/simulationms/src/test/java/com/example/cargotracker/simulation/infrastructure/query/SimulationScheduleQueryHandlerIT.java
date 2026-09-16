package com.example.cargotracker.simulation.infrastructure.query;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.cargotracker.shared.testing.AbstractAxonIntegrationTest;
import com.example.cargotracker.simulation.infrastructure.persistence.SimulationRunMapper;
import com.example.cargotracker.simulation.infrastructure.persistence.SimulationScheduleMapper;
import java.math.BigDecimal;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;

/**
 * 継続実行の統計（S94 / US36 §受入基準 3・8）。
 *
 * <p><b>明細から数える。</b> 走らせるたびに足し込む形は、記入漏れが赤くならず
 * 永久に残る（IT4 の「インデックスの累計は明細から導く」）。</p>
 *
 * <p><b>数えるのは自分が作った行だけ。</b> 共有の表で全体を数える検査は、
 * 別の変更で赤くなり原因を指さない。</p>
 */
@SpringBootTest
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class SimulationScheduleQueryHandlerIT extends AbstractAxonIntegrationTest {

    private static final Instant AT = Instant.parse("2026-09-15T01:00:00Z");

    @Autowired
    private SimulationScheduleQueryHandler queries;

    @Autowired
    private SimulationScheduleMapper schedules;

    @Autowired
    private SimulationRunMapper runs;

    @BeforeEach
    void stopActiveSchedule() {
        var active = schedules.findActive();
        if (active != null) {
            schedules.updateStatus(active.scheduleId(), "STOPPED", AT, AT);
        }
    }

    private String seedSchedule() {
        String scheduleId = "SCH-Q-" + System.nanoTime() % 100000000L;
        schedules.insert(new SimulationScheduleMapper.ScheduleRow(scheduleId, 42L, 30, 2,
                new BigDecimal("0.20"), "RUNNING", "admin01", AT, null, AT));
        return scheduleId;
    }

    private String seedRun(String scheduleId, String status) {
        String runId = "SIM-Q-" + System.nanoTime();
        runs.insert(new SimulationRunMapper.RunRow(runId, "STANDARD", status, null,
                AT, "RUNNING".equals(status) ? null : AT, "admin01", AT, scheduleId));
        return runId;
    }

    @Test
    @DisplayName("US36 §3: 種が読める（読めないと同じ並びを再現できない）")
    void exposesTheSeed() {
        seedSchedule();

        assertThat(queries.findActive()).isNotNull()
                .satisfies(view -> assertThat(view.seed()).isEqualTo(42L))
                .satisfies(view -> assertThat(view.statusLabel()).isEqualTo("実行中"));
    }

    @Test
    @DisplayName("US36 §8: 件数・成否の内訳・失敗した工程の分布が読める")
    void countsRunsAndFailedSteps() {
        String scheduleId = seedSchedule();
        seedRun(scheduleId, "SUCCEEDED");
        seedRun(scheduleId, "SUCCEEDED");
        String failed = seedRun(scheduleId, "FAILED");
        runs.insertStep(new SimulationRunMapper.StepRow(failed, 1, "ASSIGN_ROUTE",
                "FAILED", 45L, null, 422, "経路候補がありません", AT, 0L));
        // **別の稼働の行は数えない**（共有の表で全体を数えない）。
        String other = seedRun(null, "SUCCEEDED");
        assertThat(other).isNotBlank();

        var view = queries.findActive();

        assertThat(view.runsByStatus())
                .extracting(SimulationScheduleQueries.CountView::code,
                        SimulationScheduleQueries.CountView::count)
                .containsExactlyInAnyOrder(
                        org.assertj.core.groups.Tuple.tuple("SUCCEEDED", 2),
                        org.assertj.core.groups.Tuple.tuple("FAILED", 1));
        assertThat(view.failuresByStep())
                .as("**どの工程で止まりやすいかが、いちばん見たい形である**")
                .singleElement()
                .satisfies(count -> {
                    assertThat(count.code()).isEqualTo("ASSIGN_ROUTE");
                    assertThat(count.label())
                            .as("呼び名は列挙が持つ（画面が対応表を持つと片方だけ古くなる）")
                            .isNotBlank()
                            .isNotEqualTo("ASSIGN_ROUTE");
                    assertThat(count.count()).isEqualTo(1);
                });
    }

    @Test
    @DisplayName("止めても最後の 1 本は読める（統計が消えない）")
    void keepsTheLastScheduleReadableAfterItStops() {
        // **止めた瞬間に統計が読めなくなってはいけない**（IT17 のレビュー H7）。
        // 「何件流して何件落ちたか」を見るのは、たいてい**止めたあと**である。
        // **自分のものがいちばん新しいことを、時刻で決める。** 共有の表なので、
        // 同じ時刻で並ぶと他のテストの行と順番が入れ替わる。
        String scheduleId = "SCH-Q-" + System.nanoTime() % 100000000L;
        Instant later = AT.plusSeconds(3600);
        schedules.insert(new SimulationScheduleMapper.ScheduleRow(scheduleId, 42L, 30, 2,
                new BigDecimal("0.20"), "RUNNING", "admin01", later, null, later));
        seedRun(scheduleId, "SUCCEEDED");
        schedules.updateStatus(scheduleId, "STOPPED", later, later);

        assertThat(queries.findActive()).isNotNull()
                .satisfies(view -> assertThat(view.scheduleId()).isEqualTo(scheduleId))
                .satisfies(view -> assertThat(view.statusLabel()).isEqualTo("停止中"))
                .satisfies(view -> assertThat(view.runsByStatus()).isNotEmpty());
    }
}
