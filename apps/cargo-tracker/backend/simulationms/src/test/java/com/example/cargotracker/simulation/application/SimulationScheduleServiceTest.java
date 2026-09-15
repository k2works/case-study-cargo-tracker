package com.example.cargotracker.simulation.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.cargotracker.shared.domain.error.BusinessRuleViolation;
import com.example.cargotracker.shared.domain.error.IllegalTransition;
import com.example.cargotracker.simulation.domain.model.valueobjects.ScenarioInput;
import com.example.cargotracker.simulation.domain.model.valueobjects.ScheduleStatus;
import com.example.cargotracker.simulation.infrastructure.config.SimulationProperties;
import com.example.cargotracker.simulation.infrastructure.config.SimulationScheduleProperties;
import com.example.cargotracker.simulation.infrastructure.persistence.SimulationRunMapper;
import com.example.cargotracker.simulation.infrastructure.persistence.SimulationScheduleMapper;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 継続実行の稼働を受け付けて流し続ける（US36 §受入基準 1・2・4・6）。
 *
 * <p><b>眠らない。</b>「次を流す頃合いか」は {@code tick()} で外から叩く
 * ——経過時間のアサートは脆弱な実装に戻しても緑になる（IT7 の教訓）。</p>
 */
class SimulationScheduleServiceTest {

    private static final Instant NOW = Instant.parse("2026-09-15T01:00:00Z");
    private static final Clock CLOCK = Clock.fixed(NOW, ZoneId.of("Asia/Tokyo"));

    private SimulationScheduleMapper.ScheduleRow stored;
    private int running;
    private final List<String> startedRuns = new ArrayList<>();

    /** 記録の口。<b>本物より甘くしない</b>——部分ユニークと同じ形で断る。 */
    private final SimulationScheduleMapper schedules = new SimulationScheduleMapper() {

        @Override
        public int insert(ScheduleRow row) {
            if (stored != null && !"STOPPED".equals(stored.status())) {
                throw new org.springframework.dao.DuplicateKeyException(
                        "uq_simulation_schedule_active");
            }
            stored = row;
            return 1;
        }

        @Override
        public int updateStatus(String scheduleId, String status, Instant stoppedAt,
                Instant projectedAt) {
            stored = new ScheduleRow(stored.scheduleId(), stored.seed(),
                    stored.intervalSeconds(), stored.maxConcurrent(), stored.exceptionRatio(),
                    status, stored.startedBy(), stored.startedAt(), stoppedAt, projectedAt);
            return 1;
        }

        @Override
        public ScheduleRow find(String scheduleId) {
            return stored;
        }

        @Override
        public ScheduleRow findActive() {
            return stored == null || "STOPPED".equals(stored.status()) ? null : stored;
        }

        @Override
        public int countRunning(String scheduleId) {
            return running;
        }

        @Override
        public List<CountRow> countByStatus(String scheduleId) {
            return List.of();
        }

        @Override
        public List<CountRow> countFailedStepsByKind(String scheduleId) {
            return List.of();
        }
    };

    private boolean runRefuses;

    private final SimulationService simulations = new SimulationService(
            new NoRunMapper(), new SimulationProperties(true, "http://gw"),
            (input, listener) -> {
                throw new IllegalStateException("走らせない");
            }, command -> { }, CLOCK) {

        @Override
        public String start(ScenarioInput input, String scheduleId, String startedBy) {
            if (runRefuses) {
                throw new IllegalTransition("そのシナリオは実行中です");
            }
            startedRuns.add(input.scenario().name() + "@" + scheduleId);
            return "SIM-" + startedRuns.size();
        }
    };

    private SimulationScheduleService service(boolean enabled) {
        return new SimulationScheduleService(schedules, simulations,
                new SimulationScheduleProperties(enabled, Duration.ofSeconds(30), 2,
                        new BigDecimal("0.20")),
                CLOCK);
    }

    @Test
    @DisplayName("US36 §6: 許可していない環境では始められない")
    void refusesWhenNotEnabled() {
        assertThatThrownBy(() -> service(false).start(42L, "admin01"))
                .as("**流し続ける側は業務を止めうる。** 実行の許可とは別の段にする")
                .isInstanceOf(BusinessRuleViolation.class);
    }

    @Test
    @DisplayName("US36 §3: 種を指定しなければ作って記録する（再現できる形にする）")
    void recordsTheSeedEvenWhenItWasNotGiven() {
        service(true).start(null, "admin01");

        assertThat(stored.seed())
                .as("**記録しないと、あとから同じ並びを再現できない**")
                .isEqualTo(NOW.toEpochMilli());
    }

    @Test
    @DisplayName("US36 §2: 稼働は 1 本（断りに動いている稼働の識別子を添える）")
    void refusesASecondSchedule() {
        var service = service(true);
        String first = service.start(42L, "admin01");

        assertThatThrownBy(() -> service.start(43L, "admin01"))
                .isInstanceOf(IllegalTransition.class)
                .as("**「動いています」だけでは、その稼働へ行けない**")
                .hasMessageContaining(first);
    }

    @Test
    @DisplayName("US36 §1・§2: 上限まで流し、超えたら流さない")
    void startsUpToTheLimit() {
        var service = service(true);
        service.start(42L, "admin01");

        running = 0;
        assertThat(service.tick()).isNotNull();
        running = 1;
        assertThat(service.tick()).isNotNull();
        running = 2;
        assertThat(service.tick())
                .as("**上限ちょうどでは始めない**（「以下」と「未満」の取り違え）")
                .isNull();
        assertThat(startedRuns).allSatisfy(started ->
                assertThat(started).contains(stored.scheduleId()));
    }

    @Test
    @DisplayName("US36 §4: 止めると新しい実行を始めず、決着したら停止中になる")
    void stopsAndSettles() {
        var service = service(true);
        service.start(42L, "admin01");
        running = 1;

        service.stop();
        assertThat(stored.status()).isEqualTo(ScheduleStatus.STOPPING.name());
        assertThat(service.tick())
                .as("**止めると言われたら、上限に余裕があっても始めない**")
                .isNull();
        assertThat(stored.status())
                .as("走っているあいだは停止中にしない（画面が嘘をつく）")
                .isEqualTo(ScheduleStatus.STOPPING.name());

        running = 0;
        service.tick();
        assertThat(stored.status()).isEqualTo(ScheduleStatus.STOPPED.name());
    }

    @Test
    @DisplayName("US33 §5 の守りは継続実行でも外さない（引いたシナリオが走っていれば見送る）")
    void skipsWhenTheDrawnScenarioIsBusy() {
        var service = service(true);
        service.start(42L, "admin01");
        runRefuses = true;

        assertThat(service.tick())
                .as("**外すと、手で流した実行と突き合わせられなくなる**")
                .isNull();
    }

    @Test
    @DisplayName("動いている稼働が無ければ、止める先も流す先も無い")
    void doesNothingWithoutAnActiveSchedule() {
        var service = service(true);

        assertThat(service.tick()).isNull();
        assertThat(service.activeOrNull()).isNull();
        assertThatThrownBy(service::stop).isInstanceOf(IllegalTransition.class);
    }

    /** 実行の記録は触らない（この検査の対象ではない）。 */
    private static final class NoRunMapper implements SimulationRunMapper {

        @Override
        public int insert(RunRow row) {
            return 1;
        }

        @Override
        public int updateStatus(String runId, String status, Instant finishedAt,
                Instant projectedAt) {
            return 1;
        }

        @Override
        public int insertStep(StepRow row) {
            return 1;
        }

        @Override
        public RunRow find(String runId) {
            return null;
        }

        @Override
        public List<RunRow> findRecent(int limit) {
            return List.of();
        }

        @Override
        public RunRow findRunning(String scenarioId) {
            return null;
        }

        @Override
        public List<StepRow> findSteps(String runId) {
            return List.of();
        }
    }
}
