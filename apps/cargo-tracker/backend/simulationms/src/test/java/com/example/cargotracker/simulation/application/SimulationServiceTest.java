package com.example.cargotracker.simulation.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.cargotracker.shared.domain.error.BusinessRuleViolation;
import com.example.cargotracker.shared.domain.error.IllegalTransition;
import com.example.cargotracker.simulation.domain.model.valueobjects.Scenario;
import com.example.cargotracker.simulation.domain.model.valueobjects.StepKind;
import com.example.cargotracker.simulation.infrastructure.config.SimulationProperties;
import com.example.cargotracker.simulation.infrastructure.persistence.SimulationRunMapper;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** 実行の受け付け（US33 §受入基準 4・5 / US34 §受入基準 1）。 */
class SimulationServiceTest {

    private static final Instant NOW = Instant.parse("2026-09-14T01:00:00Z");
    private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);

    private final List<SimulationRunMapper.RunRow> inserted = new ArrayList<>();
    private final List<SimulationRunMapper.StepRow> steps = new ArrayList<>();
    private final List<String> statuses = new ArrayList<>();
    private SimulationRunMapper.RunRow running;

    /** 次の {@code insert} を制約違反にする（同時押しの再現）。 */
    private boolean insertCollides;

    /** 衝突したあとに読める「勝った実行」。既定は無い（先に消えた場合）。 */
    private SimulationRunMapper.RunRow runningAfterRace;

    /** {@code findRunning} の 1 度目かどうか。 */
    private boolean runningSeenFirst = true;

    /** 記録の口。<b>本物より甘くしない</b>——書いた行をそのまま持つ。 */
    private final SimulationRunMapper runs = new SimulationRunMapper() {

        @Override
        public int insert(RunRow row) {
            if (insertCollides) {
                // **本物より甘くしない。** 部分ユニーク索引は同じ形の例外を投げる。
                throw new org.springframework.dao.DuplicateKeyException(
                        "uq_simulation_run_running");
            }
            inserted.add(row);
            return 1;
        }

        @Override
        public int updateStatus(String runId, String status, Instant finishedAt,
                Instant projectedAt) {
            statuses.add(runId + ":" + status);
            return 1;
        }

        @Override
        public int insertStep(StepRow row) {
            steps.add(row);
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
            // **同時押しはここで再現する。** 1 度目は「実行中は無い」と答え、
            // 書き込みで衝突したあとの 2 度目に勝った側を返す——実物の競り合いが
            // まさにこの順で見える。
            RunRow answer = runningSeenFirst ? running : runningAfterRace;
            runningSeenFirst = false;
            return answer;
        }

        @Override
        public List<StepRow> findSteps(String runId) {
            return List.of();
        }
    };

    private SimulationService service(boolean enabled, BusinessApi api) {
        return new SimulationService(runs, new SimulationProperties(enabled, "http://gw"),
                (scenario, listener) -> new SimulationRunner(api, (kind, produced) -> true,
                        duration -> { }, CLOCK, listener),
                // **同じ糸で走らせる**——検査は終わってから見たい。
                Runnable::run, CLOCK);
    }

    @Test
    @DisplayName("US33 §4: 無効な環境では実行を断る")
    void refusesWhenDisabled() {
        assertThatThrownBy(() -> service(false, (kind, produced) ->
                BusinessApi.StepResult.success(null)).start(Scenario.STANDARD, "admin01"))
                .isInstanceOf(BusinessRuleViolation.class)
                .hasMessageContaining("実行できません");
        assertThat(inserted).isEmpty();
    }

    @Test
    @DisplayName("US33 §5: 同じシナリオが実行中なら、その実行の識別子を添えて断る")
    void refusesConcurrentRunAndNamesIt() {
        running = new SimulationRunMapper.RunRow("SIM-running", "STANDARD", "RUNNING",
                null, NOW, null, "admin01", NOW, null);

        assertThatThrownBy(() -> service(true, (kind, produced) ->
                BusinessApi.StepResult.success(null)).start(Scenario.STANDARD, "admin01"))
                .isInstanceOf(IllegalTransition.class)
                // **識別子を添える。**「二重に実行できません」だけでは、
                // いまの結果へ行けない。
                .hasMessageContaining("SIM-running");
    }

    @Test
    @DisplayName("N9: 同時に押されても 500 にしない（利用者には「実行中です」と同じ出来事）")
    void translatesTheRaceIntoARefusal() {
        // 読んでから書くまでの隙間でもう 1 本が始まる。**守りは索引の側にあり**、
        // ここへは制約違反として届く——そのまま上げると 500 になる。
        insertCollides = true;
        running = null;

        assertThatThrownBy(() -> service(true, (kind, produced) ->
                BusinessApi.StepResult.success("ID"))
                .start(Scenario.NO_ROUTE, "admin01"))
                .isInstanceOf(IllegalTransition.class)
                .hasMessageContaining("実行中です")
                .as("**原因は残す。** 切り分けるのは記録を読む人である")
                .hasCauseInstanceOf(org.springframework.dao.DuplicateKeyException.class);
    }

    @Test
    @DisplayName("N9: 競り負けたら、勝った実行の識別子を添える（いまの結果へ行けるように）")
    void namesTheWinningRunAfterTheRace() {
        insertCollides = true;
        // 1 度目は「実行中は無い」。書き込みで衝突したあとの 2 度目に勝った側が読める。
        running = null;
        runningAfterRace = new SimulationRunMapper.RunRow("SIM-winner", Scenario.NO_ROUTE.name(),
                "RUNNING", null, Instant.EPOCH, null, "admin01", Instant.EPOCH, null);

        assertThatThrownBy(() -> service(true, (kind, produced) ->
                BusinessApi.StepResult.success("ID"))
                .start(Scenario.NO_ROUTE, "admin01"))
                .isInstanceOf(IllegalTransition.class)
                .as("**「二重に実行できません」だけでは、いまの結果へ行けない。**")
                .hasMessageContaining("SIM-winner");
    }

    @Test
    @DisplayName("実行は先に記録してから走らせる（始まった直後に読み口へ現れる）")
    void recordsTheRunBeforeExecuting() {
        String runId = service(true, (kind, produced) ->
                BusinessApi.StepResult.success("ID-" + kind.name()))
                .start(Scenario.NO_ROUTE, "admin01");

        assertThat(inserted).singleElement().satisfies(row -> {
            assertThat(row.runId()).isEqualTo(runId);
            // **列に収まる長さで作る。** VARCHAR(36) にあふれると、集約は通り
            // 記録だけが落ちる（billingms の `PAY-` と同じ形。3 度目）。
            assertThat(row.runId()).hasSizeLessThanOrEqualTo(36);
            assertThat(row.status()).isEqualTo("RUNNING");
            assertThat(row.startedBy()).isEqualTo("admin01");
        });
        // **工程は 1 件ずつ書く。** 終わってからまとめて書くと、走っている
        // あいだ画面が「どこまで進んだか」を出せない。
        assertThat(steps).hasSize(Scenario.NO_ROUTE.steps().size());
        assertThat(statuses).containsExactly(runId + ":SUCCEEDED");
    }

    @Test
    @DisplayName("止まった工程の理由が記録に残る（US34 §2）")
    void keepsTheFailureReason() {
        String runId = service(true, (kind, produced) -> kind == StepKind.ASSIGN_ROUTE
                ? BusinessApi.StepResult.failure(422, "経路の候補が 1 件もありません")
                : BusinessApi.StepResult.success(null))
                .start(Scenario.NO_ROUTE, "admin01");

        assertThat(steps).last().satisfies(step -> {
            assertThat(step.kind()).isEqualTo("ASSIGN_ROUTE");
            assertThat(step.outcome()).isEqualTo("FAILED");
            assertThat(step.failureStatus()).isEqualTo(422);
            assertThat(step.failureMessage()).contains("経路の候補");
        });
        assertThat(statuses).containsExactly(runId + ":FAILED");
    }

    @Test
    @DisplayName("予期しない中断でも実行は決着する（そのシナリオを二度と流せなくしない）")
    void settlesEvenWhenTheRunnerBlowsUp() {
        SimulationService service = new SimulationService(runs,
                new SimulationProperties(true, "http://gw"),
                (scenario, listener) -> {
                    throw new IllegalStateException("走らせ手を作れない");
                },
                Runnable::run, CLOCK);

        String runId = service.start(Scenario.STANDARD, "admin01");

        assertThat(statuses).containsExactly(runId + ":FAILED");
    }

    @Test
    @DisplayName("工程の所要時間を記録に残す（US34 §1）")
    void keepsElapsedTime() {
        service(true, (kind, produced) -> BusinessApi.StepResult.success(null))
                .start(Scenario.NO_ROUTE, "admin01");

        assertThat(steps).allSatisfy(step ->
                assertThat(step.elapsedMs()).isNotNull().isGreaterThanOrEqualTo(0L));
    }

    @Test
    @DisplayName("応答コードの無い失敗も失敗として記録する（成功にしない）")
    void treatsFailureWithoutStatusAsFailure() {
        // **応答コードだけで判定すると、コードの無い失敗が成功として通る。**
        // 工程が止まっているのに「成功」と記録された（IT16 で実測）。
        String runId = service(true, (kind, produced) -> kind == StepKind.REGISTER_BOOKING
                ? BusinessApi.StepResult.failure("読み口に現れませんでした")
                : BusinessApi.StepResult.success(null))
                .start(Scenario.NO_ROUTE, "admin01");

        assertThat(steps()).last().satisfies(step -> {
            assertThat(step.outcome()).isEqualTo("FAILED");
            assertThat(step.failureStatus()).isNull();
            assertThat(step.failureMessage()).contains("読み口に現れませんでした");
        });
        assertThat(statuses).containsExactly(runId + ":FAILED");
    }

    private List<SimulationRunMapper.StepRow> steps() {
        return steps;
    }
}
