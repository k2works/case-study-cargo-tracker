package com.example.simulationms.application.internal.commandservices;

import com.example.simulationms.domain.model.aggregates.ContinuousRunSession;
import com.example.simulationms.domain.model.valueobjects.ContinuousRunPolicy;
import com.example.simulationms.domain.model.valueobjects.ScenarioGenerator;
import com.example.simulationms.domain.model.valueobjects.Seed;
import com.example.simulationms.domain.model.valueobjects.SessionId;
import com.example.simulationms.domain.repository.ContinuousRunSessionRepository;
import java.time.Clock;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 継続実行の刻み（US37・[ADR-031] 決定 2）。
 *
 * <p><strong>simulationms の中に置く。</strong>外部のジョブ基盤に出すと、
 * 停止・再現・上限の 3 つが別の場所で管理される——画面で「止める」を押したのに
 * 外のジョブが動き続ける、という状態を作らない。
 *
 * <p>刻みそのものは外（Spring のスケジューリング）から呼ばれる。ここが持つのは
 * <strong>「いま始めてよいか」と「何を始めるか」</strong>だけである。
 */
public class ContinuousRunScheduler {

    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("yyyyMMdd");

    private final ContinuousRunSessionRepository sessions;
    private final ContinuousRunner runner;
    private final Clock clock;

    /**
     * セッションごとの乱数器。
     *
     * <p><strong>セッションの中では 1 つを使い続ける。</strong>刻むたびに種から作り直すと、
     * 毎回同じ 1 件が出る——「ランダムに流し続ける」ことにならない。
     * セッションをまたいで共有はしない（[ADR-031] 決定 1）。
     */
    private final Map<String, ScenarioGenerator> generators = new ConcurrentHashMap<>();

    public ContinuousRunScheduler(ContinuousRunSessionRepository sessions,
            ContinuousRunner runner, Clock clock) {
        this.sessions = sessions;
        this.runner = runner;
        this.clock = clock;
    }

    /**
     * 継続実行を始める（US37-4）。
     *
     * <p><strong>種を指定しなくても、使った種は記録する。</strong>記録しないと、
     * 指定しなかった実行だけが再現できない——実運用では指定しない方が普通である。
     */
    public ContinuousRunSession start(ContinuousRunPolicy policy, Seed seed, String startedBy) {
        Seed effective = seed == null ? Seed.random() : seed;
        ContinuousRunSession session = ContinuousRunSession.start(nextSessionId(), effective,
                policy, startedBy, clock.instant());
        sessions.save(session);
        generators.put(session.sessionId().value(),
                effective.newGenerator(policy.exceptionRatio()));
        return session;
    }

    /**
     * 停止する（US37-4）。
     *
     * <p>止めるのは<strong>新規の開始だけ</strong>である。進行中の実行は最後まで走る。
     */
    public ContinuousRunSession stop(SessionId sessionId) {
        ContinuousRunSession session = sessions.findById(sessionId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "そのセッションはありません: " + sessionId.value()));
        ContinuousRunSession stopped = session.stop(runner.running(), clock.instant());
        sessions.save(stopped);
        return stopped;
    }

    /**
     * 1 刻み。外から一定間隔で呼ばれる。
     *
     * <p>始めてよければ 1 件だけ始める。<strong>一度に上限まで埋めない</strong>——
     * 間隔を置いて増えていく方が、実運用の増え方に近い。
     */
    public void tick() {
        sessions.findActive().ifPresent(this::advance);
    }

    private void advance(ContinuousRunSession session) {
        int running = runner.running();
        ContinuousRunSession settled = session.settleIfFinished(running, clock.instant());
        if (settled != session) {
            sessions.save(settled);
            generators.remove(settled.sessionId().value());
            return;
        }
        if (!session.canStartAnotherRun(running)) {
            return;
        }
        ScenarioGenerator generator = generators.computeIfAbsent(session.sessionId().value(),
                key -> session.seed().newGenerator(session.policy().exceptionRatio()));
        runner.start(generator.next(), session.sessionId(), session.seed());
    }

    /**
     * その日の連番。
     *
     * <p>実行 ID と同じく、<strong>裁くのは一意制約である</strong>——ここで数えた値は
     * 空いていることの保証ではない。継続実行は同時に 2 つ始めるものではないため、
     * 実行 ID のような採り直しは置かない。
     */
    private SessionId nextSessionId() {
        String prefix = "SES-" + LocalDate.now(clock).format(DAY) + "-";
        return SessionId.of(prefix + "%04d".formatted(sessions.countStartedOn(prefix) + 1));
    }
}
