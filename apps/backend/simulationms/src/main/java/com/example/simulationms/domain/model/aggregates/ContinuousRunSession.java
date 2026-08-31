package com.example.simulationms.domain.model.aggregates;

import com.example.simulationms.domain.model.valueobjects.ContinuousRunPolicy;
import com.example.simulationms.domain.model.valueobjects.Seed;
import com.example.simulationms.domain.model.valueobjects.SessionId;
import com.example.simulationms.domain.model.valueobjects.SessionStatus;
import java.time.Instant;
import java.util.Optional;

/**
 * 継続実行の 1 回分（US37・[ADR-031] 決定 2・4）。
 *
 * <p>種と上限を持ち、いま新しい実行を始めてよいかを決める。
 *
 * <p><strong>停止は新規の開始だけを止める</strong>（決定 4）。進行中の実行を中断すると、
 * 業務データが中途半端な状態で残る——[ADR-030] 決定 5（巻き戻さない）と噛み合わない。
 * 巻き戻さないと決めた以上、中断した実行の後始末をする場所が無い。
 */
public final class ContinuousRunSession {

    private final SessionId sessionId;
    private final Seed seed;
    private final ContinuousRunPolicy policy;
    private final SessionStatus status;
    private final String startedBy;
    private final Instant startedAt;
    private final Instant stoppedAt;

    private ContinuousRunSession(SessionId sessionId, Seed seed, ContinuousRunPolicy policy,
            SessionStatus status, String startedBy, Instant startedAt, Instant stoppedAt) {
        this.sessionId = sessionId;
        this.seed = seed;
        this.policy = policy;
        this.status = status;
        this.startedBy = startedBy;
        this.startedAt = startedAt;
        this.stoppedAt = stoppedAt;
    }

    public static ContinuousRunSession start(SessionId sessionId, Seed seed,
            ContinuousRunPolicy policy, String startedBy, Instant startedAt) {
        return new ContinuousRunSession(sessionId, seed, policy, SessionStatus.RUNNING,
                startedBy, startedAt, null);
    }

    /** 永続化された行から戻す。**復元では検査しない**——新規の受け入れ時だけ検査する。 */
    public static ContinuousRunSession restore(SessionId sessionId, Seed seed,
            ContinuousRunPolicy policy, SessionStatus status, String startedBy,
            Instant startedAt, Instant stoppedAt) {
        return new ContinuousRunSession(sessionId, seed, policy, status, startedBy,
                startedAt, stoppedAt);
    }

    /**
     * いま新しい実行を始めてよいか（US37-2）。
     *
     * <p>止めたあとは、進行中が残っていても<strong>新しくは始めない</strong>。
     */
    public boolean canStartAnotherRun(int running) {
        return status == SessionStatus.RUNNING && policy.allows(running);
    }

    /**
     * 停止を指示する（US37-4）。
     *
     * <p>進行中が残っていれば {@code STOPPING} で待つ。無ければその場で止まる。
     */
    public ContinuousRunSession stop(int running, Instant at) {
        if (status != SessionStatus.RUNNING) {
            throw new IllegalStateException(
                    "実行中でないセッションは停止できません: " + status.label());
        }
        return running > 0
                ? withStatus(SessionStatus.STOPPING, null)
                : withStatus(SessionStatus.STOPPED, at);
    }

    /**
     * 進行中が尽きたら停止済みへ移す。
     *
     * <p><strong>実行中のセッションは、進行中が 0 でも止めない。</strong>間隔を待って
     * いるだけの状態と、止めた状態は違う。
     */
    public ContinuousRunSession settleIfFinished(int running, Instant at) {
        if (status != SessionStatus.STOPPING || running > 0) {
            return this;
        }
        return withStatus(SessionStatus.STOPPED, at);
    }

    private ContinuousRunSession withStatus(SessionStatus next, Instant at) {
        return new ContinuousRunSession(sessionId, seed, policy, next, startedBy, startedAt, at);
    }

    public SessionId sessionId() {
        return sessionId;
    }

    public Seed seed() {
        return seed;
    }

    public ContinuousRunPolicy policy() {
        return policy;
    }

    public SessionStatus status() {
        return status;
    }

    public String startedBy() {
        return startedBy;
    }

    public Instant startedAt() {
        return startedAt;
    }

    public Optional<Instant> stoppedAt() {
        return Optional.ofNullable(stoppedAt);
    }
}
