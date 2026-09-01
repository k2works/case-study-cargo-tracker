package com.example.simulationms.application.internal.commandservices;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.simulationms.domain.model.aggregates.ContinuousRunSession;
import com.example.simulationms.domain.model.valueobjects.ContinuousRunPolicy;
import com.example.simulationms.domain.model.valueobjects.Scenario;
import com.example.simulationms.domain.model.valueobjects.ScenarioRequest;
import com.example.simulationms.domain.model.valueobjects.Seed;
import com.example.simulationms.domain.model.valueobjects.SessionId;
import com.example.simulationms.domain.model.valueobjects.SessionStatus;
import com.example.simulationms.domain.repository.ContinuousRunSessionRepository;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * 継続実行の刻み（US37・[ADR-031] 決定 2・4）。
 *
 * <p><strong>スケジューラは simulationms の中に置く。</strong>外部のジョブ基盤に出すと、
 * 停止・再現・上限の 3 つが別の場所で管理される——画面で「止める」を押したのに
 * 外のジョブが動き続ける、という状態を作らない。
 */
@DisplayName("継続実行の刻み")
class ContinuousRunSchedulerTest {

    private static final Instant NOW = Instant.parse("2026-12-07T01:00:00Z");

    private static final Clock CLOCK = Clock.fixed(NOW, ZoneId.of("Asia/Tokyo"));

    private static final ContinuousRunPolicy POLICY =
            ContinuousRunPolicy.of(30, 3, BigDecimal.valueOf(0.2));

    /** 何を何回始めたかだけを覚える実行係。業務は呼ばない。 */
    private static final class RecordingRunner implements ContinuousRunner {

        private final List<ScenarioRequest> started = new ArrayList<>();

        /** 「まだ終わっていない」ことにする本数。 */
        private int inFlight;

        @Override
        public void start(ScenarioRequest request, SessionId sessionId, Seed seed) {
            started.add(request);
            inFlight++;
        }

        @Override
        public int running() {
            return inFlight;
        }

        void finishOne() {
            inFlight--;
        }
    }

    private static final class InMemorySessions implements ContinuousRunSessionRepository {

        private final Map<String, ContinuousRunSession> sessions = new LinkedHashMap<>();

        @Override
        public void save(ContinuousRunSession session) {
            sessions.put(session.sessionId().value(), session);
        }

        @Override
        public Optional<ContinuousRunSession> findById(SessionId sessionId) {
            return Optional.ofNullable(sessions.get(sessionId.value()));
        }

        /** **新しい順に返す。**本物と同じ並びにする——甘くすると本物の誤りを素通りさせる。 */
        @Override
        public java.util.List<ContinuousRunSession> findRecent(int limit) {
            java.util.List<ContinuousRunSession> all =
                    new java.util.ArrayList<>(sessions.values());
            java.util.Collections.reverse(all);
            return all.stream().limit(limit).toList();
        }

        @Override
        public Optional<ContinuousRunSession> findActive() {
            return sessions.values().stream()
                    .filter(session -> session.status() != SessionStatus.STOPPED)
                    .findFirst();
        }

        @Override
        public int countStartedOn(String prefix) {
            return (int) sessions.keySet().stream().filter(id -> id.startsWith(prefix)).count();
        }
    }

    private final RecordingRunner runner = new RecordingRunner();
    private final InMemorySessions sessions = new InMemorySessions();

    /**
     * 進む時計。
     *
     * <p><strong>間隔が効くようになったので、刻むだけでは次が始まらない。</strong>
     * 刻みのたびに時計を十分に進め、ここで見たいもの（上限・停止）だけを見る。
     */
    private final MutableClock clock = new MutableClock(NOW);

    private final ContinuousRunScheduler scheduler =
            new ContinuousRunScheduler(sessions, runner, clock);

    /** 間隔を待ってから刻む。 */
    private void tickAfterInterval() {
        clock.advanceSeconds(POLICY.intervalSeconds());
        scheduler.tick();
    }

    @Nested
    @DisplayName("開始と刻み")
    class Ticking {

        @Test
        @DisplayName("開始すると、種と上限を覚えたセッションができる")
        void startsASession() {
            ContinuousRunSession session = scheduler.start(POLICY, Seed.of(42L), "admin01");

            assertThat(session.status()).isEqualTo(SessionStatus.RUNNING);
            assertThat(session.seed()).isEqualTo(Seed.of(42L));
            assertThat(sessions.findActive()).isPresent();
        }

        /** <strong>種を指定しなければ、その場で作って記録する。</strong>記録しないと再現できない。 */
        @Test
        @DisplayName("種を指定しなくても、使った種は記録される")
        void recordsTheSeedEvenWhenNotGiven() {
            assertThat(scheduler.start(POLICY, null, "admin01").seed()).isNotNull();
        }

        @Test
        @DisplayName("刻むたびに、乱数が選んだ実行を始める")
        void startsARunOnEachTick() {
            scheduler.start(POLICY, Seed.of(42L), "admin01");

            scheduler.tick();

            assertThat(runner.started).hasSize(1);
            assertThat(Scenario.all().stream().map(Scenario::id))
                    .contains(runner.started.getFirst().scenario().id());
        }

        /**
         * <strong>上限を超えて開始しない</strong>（US37-2）。
         * 上限を無視する実装に戻すと、ここが赤くなる。
         */
        @Test
        @DisplayName("同時実行数が上限に達したら、刻んでも始めない")
        void doesNotStartBeyondTheLimit() {
            scheduler.start(POLICY, Seed.of(42L), "admin01");

            for (int i = 0; i < 10; i++) {
                tickAfterInterval();
            }

            assertThat(runner.started).hasSize(POLICY.maxConcurrent());
        }

        @Test
        @DisplayName("進行中が終われば、また始める")
        void startsAgainWhenARunFinishes() {
            scheduler.start(POLICY, Seed.of(42L), "admin01");
            for (int i = 0; i < 5; i++) {
                tickAfterInterval();
            }
            runner.finishOne();

            tickAfterInterval();

            assertThat(runner.started).hasSize(POLICY.maxConcurrent() + 1);
        }

        @Test
        @DisplayName("セッションが無ければ、刻んでも何も始めない")
        void doesNothingWithoutASession() {
            scheduler.tick();

            assertThat(runner.started).isEmpty();
        }

        /** <strong>同じ種からは同じ並びが出る</strong>（決定 1）。 */
        @Test
        @DisplayName("同じ種で始め直すと、同じ並びで実行される")
        void theSameSeedReplaysTheSameSequence() {
            scheduler.start(POLICY, Seed.of(42L), "admin01");
            scheduler.tick();
            String first = runner.started.getFirst().toString();

            RecordingRunner replayRunner = new RecordingRunner();
            ContinuousRunScheduler replay = new ContinuousRunScheduler(
                    new InMemorySessions(), replayRunner, CLOCK);
            replay.start(POLICY, Seed.of(42L), "admin01");
            replay.tick();

            assertThat(replayRunner.started.getLast()).hasToString(first);
        }
    }

    @Nested
    @DisplayName("停止")
    class Stopping {

        /** <strong>止めるのは新規の開始だけ</strong>（決定 4）。進行中は最後まで走る。 */
        @Test
        @DisplayName("停止すると、新しい実行は始まらない")
        void stopStartsNothingNew() {
            ContinuousRunSession session = scheduler.start(POLICY, Seed.of(42L), "admin01");
            scheduler.tick();
            int startedBeforeStop = runner.started.size();

            scheduler.stop(session.sessionId());
            scheduler.tick();

            assertThat(runner.started).hasSize(startedBeforeStop);
        }

        @Test
        @DisplayName("進行中があるうちは停止処理中で、尽きたら停止済みになる")
        void settlesWhenTheLastRunFinishes() {
            ContinuousRunSession session = scheduler.start(POLICY, Seed.of(42L), "admin01");
            tickAfterInterval();

            assertThat(scheduler.stop(session.sessionId()).status())
                    .isEqualTo(SessionStatus.STOPPING);

            runner.finishOne();
            tickAfterInterval();

            assertThat(sessions.findById(session.sessionId()).orElseThrow().status())
                    .isEqualTo(SessionStatus.STOPPED);
        }

        @Test
        @DisplayName("知らないセッションは止められない")
        void cannotStopAnUnknownSession() {
            SessionId unknown = SessionId.of("SES-20261207-9999");

            assertThatThrownBy(() -> scheduler.stop(unknown))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("SES-20261207-9999");
        }
    }

    @Nested
    @DisplayName("実行の間隔")
    class Interval {

        /**
         * <strong>設定した間隔を守る</strong>（US37-2）。
         *
         * <p>守らないと、画面が「30 秒ごと」と出すのに 1 本終われば次が始まる。
         * データが増えすぎたとき、管理者は設定を疑わず別の場所を探すことになる。
         */
        @Test
        @DisplayName("前の開始から間隔が経つまで、次を始めない")
        void waitsForTheConfiguredInterval() {
            scheduler.start(POLICY, Seed.of(42L), "admin01");

            scheduler.tick();
            assertThat(runner.started).hasSize(1);

            // 間隔（30 秒）を待たずに刻んでも、次は始まらない
            clock.advanceSeconds(29);
            scheduler.tick();
            assertThat(runner.started).hasSize(1);

            clock.advanceSeconds(1);
            scheduler.tick();
            assertThat(runner.started).hasSize(2);
        }

        /** 間隔が経っていても、上限に達していれば始めない（US37-2）。 */
        @Test
        @DisplayName("間隔が経っていても、上限を超えては始めない")
        void stillRespectsTheConcurrencyLimit() {
            scheduler.start(POLICY, Seed.of(42L), "admin01");

            for (int i = 0; i < 10; i++) {
                clock.advanceSeconds(60);
                scheduler.tick();
            }

            assertThat(runner.started).hasSize(POLICY.maxConcurrent());
        }
    }


    @Nested
    @DisplayName("停止が長引いたとき")
    class StuckStopping {

        /**
         * <strong>逃げ道を置く</strong>（IT15 のレビュー指摘）。
         *
         * <p>進行中の本数はメモリ上の数であり、1 件詰まると停止処理中から戻らない。
         * そのあいだ新しいセッションも始められない——復旧が Pod の再起動だけに
         * なる。時間で見切る。
         */
        @Test
        @DisplayName("停止処理中が長引いたら、見切って停止済みにする")
        void givesUpWaitingAfterTheDeadline() {
            ContinuousRunSession session = scheduler.start(POLICY, Seed.of(42L), "admin01");
            tickAfterInterval();
            scheduler.stop(session.sessionId());

            // 進行中が残ったまま。見切りの時間までは待つ
            clock.advanceSeconds(ContinuousRunScheduler.STOP_DEADLINE.toSeconds() - 1);
            scheduler.tick();
            assertThat(sessions.findById(session.sessionId()).orElseThrow().status())
                    .isEqualTo(SessionStatus.STOPPING);

            clock.advanceSeconds(2);
            scheduler.tick();
            assertThat(sessions.findById(session.sessionId()).orElseThrow().status())
                    .isEqualTo(SessionStatus.STOPPED);
        }
    }

    /** 進む時計。**実時計を使わない**——待つテストは遅く、脆い。 */
    private static final class MutableClock extends Clock {

        private Instant now;

        MutableClock(Instant now) {
            this.now = now;
        }

        void advanceSeconds(long seconds) {
            now = now.plusSeconds(seconds);
        }

        @Override
        public ZoneId getZone() {
            return ZoneId.of("Asia/Tokyo");
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    }
}