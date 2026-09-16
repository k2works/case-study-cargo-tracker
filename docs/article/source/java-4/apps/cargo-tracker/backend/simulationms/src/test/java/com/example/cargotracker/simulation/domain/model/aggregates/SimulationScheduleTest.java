package com.example.cargotracker.simulation.domain.model.aggregates;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.cargotracker.shared.domain.error.BusinessRuleViolation;
import com.example.cargotracker.shared.domain.error.IllegalTransition;
import com.example.cargotracker.simulation.domain.model.valueobjects.ScheduleStatus;
import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 継続実行の稼働（US36 §受入基準 2・4）。
 *
 * <p><b>上限は集約が守る。</b> 呼ぶ側で数えてから始める形は、要求が同時に
 * 来たときに両方とも通る。</p>
 */
class SimulationScheduleTest {

    private static final Instant NOW = Instant.parse("2026-09-15T01:00:00Z");

    private static SimulationSchedule started() {
        return SimulationSchedule.start("SCH-1", 42L, Duration.ofSeconds(30), 3,
                new java.math.BigDecimal("0.20"), "admin01", NOW);
    }

    @Test
    @DisplayName("US36 §4: 始めると実行中になり、乱数の種が残る")
    void startsRunningAndRemembersTheSeed() {
        SimulationSchedule schedule = started();

        assertThat(schedule.status()).isEqualTo(ScheduleStatus.RUNNING);
        assertThat(schedule.seed())
                .as("**種が読めないと再現できない**（US36 §3）")
                .isEqualTo(42L);
    }

    @Test
    @DisplayName("US36 §2: 同時実行の上限を 1 でも超える要求は断る")
    void refusesToExceedTheConcurrencyLimit() {
        SimulationSchedule schedule = started();

        assertThat(schedule.canStartAnother(2)).isTrue();
        assertThat(schedule.canStartAnother(3))
                .as("**上限ちょうどでも始めない。** 「以下」と「未満」の取り違えで 1 本増える")
                .isFalse();
        assertThat(schedule.canStartAnother(4)).isFalse();
    }

    @Test
    @DisplayName("US36 §4: 止めると停止処理中になり、新しい実行を始めない")
    void stopsAcceptingNewRuns() {
        SimulationSchedule schedule = started();

        schedule.stop(NOW.plusSeconds(60));

        assertThat(schedule.status()).isEqualTo(ScheduleStatus.STOPPING);
        assertThat(schedule.canStartAnother(0))
                .as("**走っている実行が無くても始めない。** 止めると言われている")
                .isFalse();
    }

    @Test
    @DisplayName("US36 §4: 走っている実行が全部決着したら停止中になる")
    void settlesWhenTheLastRunFinishes() {
        SimulationSchedule schedule = started();
        schedule.stop(NOW.plusSeconds(60));

        schedule.settleIfDrained(1, NOW.plusSeconds(70));
        assertThat(schedule.status())
                .as("**走っているあいだは停止中にしない。** 画面が嘘をつく")
                .isEqualTo(ScheduleStatus.STOPPING);

        schedule.settleIfDrained(0, NOW.plusSeconds(90));
        assertThat(schedule.status()).isEqualTo(ScheduleStatus.STOPPED);
        assertThat(schedule.stoppedAt()).isEqualTo(NOW.plusSeconds(90));
    }

    @Test
    @DisplayName("実行中でないものを止めない（終わった稼働を書き換えない）")
    void refusesToStopWhatIsNotRunning() {
        SimulationSchedule schedule = started();
        schedule.stop(NOW.plusSeconds(60));
        schedule.settleIfDrained(0, NOW.plusSeconds(70));

        assertThatThrownBy(() -> schedule.stop(NOW.plusSeconds(80)))
                .isInstanceOf(IllegalTransition.class)
                .hasMessageContaining("停止中");
    }

    @Test
    @DisplayName("US36 §2: 設定の値そのものを断る（上限 0・間隔 0・比率が範囲外）")
    void refusesUnusableSettings() {
        assertThatThrownBy(() -> SimulationSchedule.start("SCH-1", 1L,
                Duration.ofSeconds(30), 0, java.math.BigDecimal.ZERO, "admin01", NOW))
                .as("上限 0 は「動かない稼働」——始められたことにしない")
                .isInstanceOf(BusinessRuleViolation.class);
        assertThatThrownBy(() -> SimulationSchedule.start("SCH-1", 1L,
                Duration.ZERO, 1, java.math.BigDecimal.ZERO, "admin01", NOW))
                .as("間隔 0 は**業務を止める**（局面の危険 3）")
                .isInstanceOf(BusinessRuleViolation.class);
        assertThatThrownBy(() -> SimulationSchedule.start("SCH-1", 1L,
                Duration.ofSeconds(30), 1, new java.math.BigDecimal("1.5"), "admin01", NOW))
                .isInstanceOf(BusinessRuleViolation.class);
    }

    @Test
    @DisplayName("US36 §2: 設定の値を 1 つずつ数え上げて断る（漏れた 1 つが業務を止める）")
    void refusesEachUnusableSetting() {
        // **名簿ではなく数え上げる。** 1 本ずつ思いついた順に検査すると、
        // 次に足した設定の検査が漏れる。
        assertThatThrownBy(() -> SimulationSchedule.start("SCH-1", 1L,
                Duration.ofSeconds(-1), 1, java.math.BigDecimal.ZERO, "admin01", NOW))
                .as("負の間隔")
                .isInstanceOf(BusinessRuleViolation.class);
        assertThatThrownBy(() -> SimulationSchedule.start("SCH-1", 1L,
                null, 1, java.math.BigDecimal.ZERO, "admin01", NOW))
                .as("間隔が無い")
                .isInstanceOf(BusinessRuleViolation.class);
        assertThatThrownBy(() -> SimulationSchedule.start("SCH-1", 1L,
                Duration.ofSeconds(30), -1, java.math.BigDecimal.ZERO, "admin01", NOW))
                .as("負の同時実行数")
                .isInstanceOf(BusinessRuleViolation.class);
        assertThatThrownBy(() -> SimulationSchedule.start("SCH-1", 1L,
                Duration.ofSeconds(30), 1, null, "admin01", NOW))
                .as("比率が無い")
                .isInstanceOf(BusinessRuleViolation.class);
        assertThatThrownBy(() -> SimulationSchedule.start("SCH-1", 1L,
                Duration.ofSeconds(30), 1, new java.math.BigDecimal("-0.1"), "admin01", NOW))
                .as("負の比率")
                .isInstanceOf(BusinessRuleViolation.class);
        assertThatThrownBy(() -> SimulationSchedule.start("SCH-1", 1L,
                Duration.ofSeconds(30), 1, java.math.BigDecimal.ZERO, null, NOW))
                .as("実行した人が無い")
                .isInstanceOf(BusinessRuleViolation.class);
    }

    @Test
    @DisplayName("US36 §4: 状態の呼び名と振る舞いを値の一覧から回して確かめる")
    void everyStatusAnswersConsistently() {
        // **列挙に値を足したら全箇所を回る。** 扱っていない値は名乗り出ない。
        for (ScheduleStatus status : ScheduleStatus.values()) {
            assertThat(status.label()).isNotBlank();
            assertThat(status.acceptsNewRuns())
                    .as("新しい実行を始めてよいのは実行中だけ: %s", status)
                    .isEqualTo(status == ScheduleStatus.RUNNING);
            assertThat(status.isStopped())
                    .as("止まりきったのは停止中だけ: %s", status)
                    .isEqualTo(status == ScheduleStatus.STOPPED);
        }
    }

    @Test
    @DisplayName("停止処理中でないものは、実行が 0 本でも停止中にしない")
    void doesNotSettleWhatWasNotStopping() {
        SimulationSchedule schedule = started();

        schedule.settleIfDrained(0, NOW.plusSeconds(10));

        assertThat(schedule.status())
                .as("止めると言われていない稼働を、たまたま空いたから止めない")
                .isEqualTo(ScheduleStatus.RUNNING);
    }

    @Test
    @DisplayName("誰が始めたか分からない稼働を作らない")
    void requiresWhoStartedIt() {
        assertThatThrownBy(() -> SimulationSchedule.start("SCH-1", 1L,
                Duration.ofSeconds(30), 1, java.math.BigDecimal.ZERO, "  ", NOW))
                .isInstanceOf(BusinessRuleViolation.class);
    }
}
