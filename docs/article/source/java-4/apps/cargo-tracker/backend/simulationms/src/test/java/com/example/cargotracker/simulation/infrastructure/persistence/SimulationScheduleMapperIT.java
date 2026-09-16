package com.example.cargotracker.simulation.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.cargotracker.shared.testing.AbstractAxonIntegrationTest;
import java.math.BigDecimal;
import java.time.Instant;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.test.annotation.DirtiesContext;

/**
 * 継続実行の稼働の記録（US36 §受入基準 2・4）。
 *
 * <p><b>稼働が 2 本走ると上限が 2 倍になる。</b> 「設定した上限を超えて実行しない」
 * （§2）は、稼働が 1 本であることに乗っている——守りは DB に置く。</p>
 *
 * <p><b>記録の側も検査する。</b> 書いて、読んで、期待値と比べるまでを 1 本で通す。</p>
 */
@SpringBootTest
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class SimulationScheduleMapperIT extends AbstractAxonIntegrationTest {

    private static final Instant AT = Instant.parse("2026-09-15T01:00:00Z");

    @Autowired
    private SimulationScheduleMapper schedules;

    @Autowired
    private SimulationRunMapper runs;

    /**
     * 動いている稼働を片付けてから始める。
     *
     * <p><b>稼働は 1 本だけという守りが、検査どうしの干渉になる。</b> 前の検査が
     * 残した稼働で次の検査の 1 本目が断られると、原因を指さない赤になる
     * （「数えるのは自分が作った行だけ」の裏返し）。</p>
     */
    @org.junit.jupiter.api.BeforeEach
    void stopActiveSchedule() {
        var active = schedules.findActive();
        if (active != null) {
            schedules.updateStatus(active.scheduleId(), "STOPPED", AT, AT);
        }
    }

    private static SimulationScheduleMapper.ScheduleRow running(String scheduleId) {
        return new SimulationScheduleMapper.ScheduleRow(scheduleId, 42L, 30, 3,
                new BigDecimal("0.20"), "RUNNING", "admin01", AT, null, AT);
    }

    @Test
    @DisplayName("US36 §4: 稼働を書いて読み直せる（設定がそのまま残る）")
    void writesAndReadsTheSchedule() {
        String scheduleId = "SCH-" + System.nanoTime();
        schedules.insert(running(scheduleId));

        assertThat(schedules.find(scheduleId)).isNotNull().satisfies(row -> {
            assertThat(row.seed()).isEqualTo(42L);
            assertThat(row.maxConcurrent()).isEqualTo(3);
            assertThat(row.intervalSeconds()).isEqualTo(30);
            assertThat(row.exceptionRatio()).isEqualByComparingTo("0.20");
        });
        schedules.updateStatus(scheduleId, "STOPPED", AT.plusSeconds(60), AT);
        assertThat(schedules.findActive())
                .as("止まりきった稼働は「動いている」に数えない")
                .isNull();
    }

    @Test
    @DisplayName("US36 §2: 動いている稼働は 1 本だけ（2 本走ると上限が 2 倍になる）")
    void refusesASecondActiveSchedule() {
        schedules.insert(running("SCH-A-" + System.nanoTime()));

        assertThatThrownBy(() -> schedules.insert(running("SCH-B-" + System.nanoTime())))
                .as("**数えてから入れる形にしない。** 同時に来た 2 つの要求が両方とも通る")
                .isInstanceOf(DuplicateKeyException.class);
    }

    @Test
    @DisplayName("US36 §2: 数えるのはその稼働が流した実行だけ（手で流した分を混ぜない）")
    void countsOnlyItsOwnRuns() {
        String scheduleId = "SCH-C-" + System.nanoTime();
        schedules.insert(running(scheduleId));
        // 手で流した実行（schedule_id が NULL）。**数えると上限が早く埋まる。**
        runs.insert(new SimulationRunMapper.RunRow("SIM-manual-" + System.nanoTime(),
                "STANDARD", "RUNNING", null, AT, null, "admin01", AT, null));

        assertThat(schedules.countRunning(scheduleId))
                .as("共有の表で全体を数える検査は、別の変更で赤くなり原因を指さない")
                .isZero();
    }
}
