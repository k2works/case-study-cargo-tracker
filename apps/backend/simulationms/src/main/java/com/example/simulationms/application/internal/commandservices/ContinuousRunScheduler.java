package com.example.simulationms.application.internal.commandservices;

import com.example.simulationms.domain.model.aggregates.ContinuousRunSession;
import com.example.simulationms.domain.model.valueobjects.ContinuousRunPolicy;
import com.example.simulationms.domain.model.valueobjects.ScenarioGenerator;
import com.example.simulationms.domain.model.valueobjects.Seed;
import com.example.simulationms.domain.model.valueobjects.SessionId;
import com.example.simulationms.domain.repository.ContinuousRunSessionRepository;
import java.time.Clock;
import java.time.Instant;
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

    /**
     * 停止処理中を待つ上限。
     *
     * <p><strong>逃げ道を置く。</strong>進行中の本数はこのインスタンスのメモリ上の
     * 数であり、1 件詰まると停止処理中から戻らない。そのあいだ新しいセッションも
     * 始められない（動いているセッションは 1 つまで）——復旧が Pod の再起動だけに
     * なる。実行 1 本は 14〜18 工程で 10 秒前後、上限まで並んでも数分で終わる。
     */
    public static final java.time.Duration STOP_DEADLINE = java.time.Duration.ofMinutes(15);

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

    /**
     * セッションごとの、最後に実行を始めた時刻。
     *
     * <p><strong>間隔は実際に効かせる。</strong>設定を保存して表示するだけだと、
     * 画面が「30 秒ごと」と出すのに 1 本終われば次が始まる——データが増えすぎた
     * とき、管理者は設定を疑わず別の場所を探すことになる。
     */
    private final Map<String, Instant> lastStartedAt = new ConcurrentHashMap<>();

    /** セッションごとの、停止を指示した時刻。見切りの判定に使う。 */
    private final Map<String, Instant> stopRequestedAt = new ConcurrentHashMap<>();

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
        if (stopped.status() == com.example.simulationms.domain.model.valueobjects
                .SessionStatus.STOPPING) {
            stopRequestedAt.put(sessionId.value(), clock.instant());
        }
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
        // **見切った本数は 0 として扱う。** 待ち続けると、止まらないまま
        // 新しいセッションも始められない状態が残る
        int running = stopDeadlinePassed(session) ? 0 : runner.running();
        ContinuousRunSession settled = session.settleIfFinished(running, clock.instant());
        if (settled != session) {
            sessions.save(settled);
            generators.remove(settled.sessionId().value());
            lastStartedAt.remove(settled.sessionId().value());
            stopRequestedAt.remove(settled.sessionId().value());
            return;
        }
        if (!session.canStartAnotherRun(running) || !intervalElapsed(session)) {
            return;
        }
        lastStartedAt.put(session.sessionId().value(), clock.instant());
        ScenarioGenerator generator = generators.computeIfAbsent(session.sessionId().value(),
                key -> session.seed().newGenerator(session.policy().exceptionRatio()));
        runner.start(generator.next(), session.sessionId(), session.seed());
    }

    /**
     * 停止を指示してから、待つ上限を過ぎたか。
     *
     * <p>過ぎたら進行中を待たずに停止済みへ移す。<strong>進行中の実行を止めるわけ
     * ではない</strong>——最後まで走る（[ADR-031] 決定 4）。止まらないセッションを
     * 残さないための見切りである。
     */
    private boolean stopDeadlinePassed(ContinuousRunSession session) {
        Instant requestedAt = stopRequestedAt.get(session.sessionId().value());
        return requestedAt != null
                && !clock.instant().isBefore(requestedAt.plus(STOP_DEADLINE));
    }

    /**
     * 前の開始から、設定した間隔が経っているか（US37-2）。
     *
     * <p>まだ 1 本も始めていなければ、待たずに始める——開始した直後に何も
     * 起きないと、動いているのかどうかが分からない。
     */
    private boolean intervalElapsed(ContinuousRunSession session) {
        Instant last = lastStartedAt.get(session.sessionId().value());
        if (last == null) {
            return true;
        }
        return !clock.instant().isBefore(
                last.plusSeconds(session.policy().intervalSeconds()));
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
