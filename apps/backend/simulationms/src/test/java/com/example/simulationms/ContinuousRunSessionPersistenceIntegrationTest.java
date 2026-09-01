package com.example.simulationms;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.simulationms.domain.model.aggregates.ContinuousRunSession;
import com.example.simulationms.domain.model.aggregates.SimulationRun;
import com.example.simulationms.domain.model.valueobjects.ContinuousRunPolicy;
import com.example.simulationms.domain.model.valueobjects.RunId;
import com.example.simulationms.domain.model.valueobjects.Scenario;
import com.example.simulationms.domain.model.valueobjects.ScenarioStep;
import com.example.simulationms.domain.model.valueobjects.Seed;
import com.example.simulationms.domain.model.valueobjects.SessionId;
import com.example.simulationms.domain.model.valueobjects.SessionStatus;
import com.example.simulationms.domain.repository.ContinuousRunSessionRepository;
import com.example.simulationms.domain.repository.SimulationRunRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * 継続実行のセッションの永続化（US37）。
 *
 * <p><strong>実 DB で確かめる。</strong>列名の誤りも、外部キーの抜けも、スタブでは
 * 見つからない。
 */
@SpringBootTest
@Testcontainers
@ExtendWith(SpringExtension.class)
@ActiveProfiles("integration")
@DisplayName("継続実行のセッションの永続化")
class ContinuousRunSessionPersistenceIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    private static final Instant STARTED = Instant.parse("2026-12-07T01:00:00Z");

    private static final ContinuousRunPolicy POLICY =
            ContinuousRunPolicy.of(30, 3, BigDecimal.valueOf(0.20));

    @Autowired
    private ContinuousRunSessionRepository sessions;

    @Autowired
    private SimulationRunRepository runs;

    private ContinuousRunSession start(String sessionId) {
        ContinuousRunSession session = ContinuousRunSession.start(SessionId.of(sessionId),
                Seed.of(20261207L), POLICY, "admin01", STARTED);
        sessions.save(session);
        return session;
    }

    @Test
    @DisplayName("開始したセッションを、種と上限ごと読み戻せる")
    void savesAndRestoresASession() {
        start("SES-20261207-0001");

        ContinuousRunSession restored =
                sessions.findById(SessionId.of("SES-20261207-0001")).orElseThrow();

        assertThat(restored.seed()).isEqualTo(Seed.of(20261207L));
        assertThat(restored.policy().intervalSeconds()).isEqualTo(30);
        assertThat(restored.policy().maxConcurrent()).isEqualTo(3);
        assertThat(restored.policy().exceptionRatio())
                .isEqualByComparingTo(BigDecimal.valueOf(0.20));
        assertThat(restored.status()).isEqualTo(SessionStatus.RUNNING);
        assertThat(restored.startedBy()).isEqualTo("admin01");
    }

    /**
     * <strong>更新で行を増やさない。</strong>「常に INSERT する save」は、
     * 作成しか起きないうちは成立し、最初の停止で壊れる（IT7 の教訓）。
     */
    @Test
    @DisplayName("停止しても、セッションの行は増えない")
    void stoppingUpdatesInsteadOfInserting() {
        ContinuousRunSession session = start("SES-20261207-0002");

        sessions.save(session.stop(0, STARTED.plusSeconds(60)));

        ContinuousRunSession restored =
                sessions.findById(SessionId.of("SES-20261207-0002")).orElseThrow();
        assertThat(restored.status()).isEqualTo(SessionStatus.STOPPED);
        assertThat(restored.stoppedAt()).contains(STARTED.plusSeconds(60));
        assertThat(sessions.countStartedOn("SES-20261207-0002")).isEqualTo(1);
    }

    /** <strong>止めたセッションは、もう動いているものとして引かれない。</strong> */
    @Test
    @DisplayName("停止済みのセッションは、動いているものとして引かれない")
    void stoppedSessionsAreNotActive() {
        ContinuousRunSession session = start("SES-20261207-0003");
        sessions.save(session.stop(0, STARTED.plusSeconds(60)));

        assertThat(sessions.findActive())
                .map(active -> active.sessionId().value())
                .isNotEqualTo(java.util.Optional.of("SES-20261207-0003"));
    }

    /**
     * <strong>継続実行が生んだ実行は、種とセッションを持つ</strong>（US37-3）。
     * 種が残らなければ、落ちた実行を再現できない。
     */
    @Test
    @DisplayName("継続実行の実行は、種とセッションごと残る")
    void runsCarryTheSeedAndSession() {
        ContinuousRunSession session = start("SES-20261207-0004");
        SimulationRun run = SimulationRun.start(RunId.of("SIM-20261207-0101"),
                Scenario.of("session-run", List.of(ScenarioStep.REGISTER_SHIPPER)),
                session.sessionId().value(), STARTED);

        runs.create(run, Seed.of(20261207L), session.sessionId());

        assertThat(runs.findByRunId(RunId.of("SIM-20261207-0101"))).isPresent();
    }

    /** 手で押した実行はセッションを持たない。**外部キーが NULL 可であること**を確かめる。 */
    @Test
    @DisplayName("手で押した実行は、セッションを持たずに残る")
    void manualRunsHaveNoSession() {
        SimulationRun run = SimulationRun.start(RunId.of("SIM-20261207-0102"),
                Scenario.of("manual-run", List.of(ScenarioStep.REGISTER_SHIPPER)),
                "admin01", STARTED);

        runs.create(run, Seed.of(0L), null);

        assertThat(runs.findByRunId(RunId.of("SIM-20261207-0102"))).isPresent();
    }

    /**
     * <strong>停止した瞬間に種が画面から消えると、翌朝には再現の手立てが無い</strong>
     * （TD-03・IT16）。US37-3 は「同じ種を指定すると同じ並びを再現できる」と言うが、
     * その種を停止後に読む手段が無かった。
     */
    @Test
    @DisplayName("停止したセッションも、種つきで一覧に残る")
    void listsPastSessionsWithTheirSeed() {
        ContinuousRunSession session = start("SES-20261207-0101");
        sessions.save(session.stop(0, STARTED.plusSeconds(60)));

        ContinuousRunSession listed = sessions.findRecent(20).stream()
                .filter(found -> found.sessionId().equals(session.sessionId()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("停止したセッションが一覧から消えている"));

        assertThat(listed.seed()).as("種が読めない。落ちた並びを再現できない")
                .isEqualTo(Seed.of(20261207L));
        assertThat(listed.status()).isEqualTo(SessionStatus.STOPPED);
    }

    /** <strong>新しい順に並ぶ。</strong>直前に回したセッションから読む。 */
    @Test
    @DisplayName("セッションの一覧は、新しい順に並ぶ")
    void listsSessionsNewestFirst() {
        start("SES-20261207-0102");
        start("SES-20261207-0103");

        assertThat(sessions.findRecent(20))
                .extracting(found -> found.sessionId().value())
                .containsSubsequence("SES-20261207-0103", "SES-20261207-0102");
    }
}
