package com.example.cargotracker.simulation.domain.model.aggregates;

import com.example.cargotracker.shared.domain.error.BusinessRuleViolation;
import com.example.cargotracker.shared.domain.error.IllegalTransition;
import com.example.cargotracker.simulation.domain.model.services.RandomScenario;
import com.example.cargotracker.simulation.domain.model.valueobjects.ScenarioInput;
import com.example.cargotracker.simulation.domain.model.valueobjects.ScheduleStatus;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;

/**
 * 継続実行の稼働（US36 / [ADR-0020]）。
 *
 * <p><b>実行 1 本（{@link SimulationRun}）とは寿命が違う。</b> 稼働は何時間も
 * 生き、実行は数十秒で終わる。1 つの集約に混ぜると、実行を 1 本記録するたびに
 * 稼働ごと書き直すことになる。</p>
 *
 * <p><b>Event Sourcing は使わない</b>（ADR-0020 決定 3）。稼働の履歴は業務の
 * 事実ではない。</p>
 *
 * <p><b>上限は集約が守る。</b> 呼ぶ側で数えてから始める形は、要求が同時に
 * 来たときに両方とも通る（IT16 で二重実行に同じ形を踏んだ）。</p>
 */
public final class SimulationSchedule {

    /** 発生比率の下限・上限。割合なので 0〜1 の外は意味を持たない。 */
    private static final BigDecimal MIN_RATIO = BigDecimal.ZERO;
    private static final BigDecimal MAX_RATIO = BigDecimal.ONE;

    private final String scheduleId;
    private final long seed;
    private final Duration interval;
    private final int maxConcurrent;
    private final BigDecimal exceptionRatio;
    private final String startedBy;
    private final Instant startedAt;
    private final RandomScenario random;

    private ScheduleStatus status = ScheduleStatus.RUNNING;
    private Instant stoppedAt;

    private SimulationSchedule(String scheduleId, long seed, Duration interval,
            int maxConcurrent, BigDecimal exceptionRatio, String startedBy, Instant startedAt) {
        this.scheduleId = scheduleId;
        this.seed = seed;
        this.interval = interval;
        this.maxConcurrent = maxConcurrent;
        this.exceptionRatio = exceptionRatio;
        this.startedBy = startedBy;
        this.startedAt = startedAt;
        this.random = RandomScenario.from(seed);
    }

    /**
     * 稼働を始める（US36 §受入基準 4）。
     *
     * <p><b>使えない設定を「始まった」ことにしない。</b> 上限 0 は動かない稼働、
     * 間隔 0 は<b>業務を止める</b>（この局面に固有の危険 3）。始めてから気づくと、
     * 止めるまでのあいだ実利用者の操作が通らない。</p>
     */
    public static SimulationSchedule start(String scheduleId, long seed, Duration interval,
            int maxConcurrent, BigDecimal exceptionRatio, String startedBy, Instant startedAt) {
        if (startedBy == null || startedBy.isBlank()) {
            throw new BusinessRuleViolation("実行した人は必須です");
        }
        if (maxConcurrent < 1) {
            throw new BusinessRuleViolation("同時実行数は 1 以上である必要があります");
        }
        if (interval == null || interval.isZero() || interval.isNegative()) {
            throw new BusinessRuleViolation(
                    "実行間隔は 1 秒以上である必要があります（間を空けないと業務が止まります）");
        }
        if (exceptionRatio == null
                || exceptionRatio.compareTo(MIN_RATIO) < 0
                || exceptionRatio.compareTo(MAX_RATIO) > 0) {
            throw new BusinessRuleViolation("例外の発生比率は 0 以上 1 以下である必要があります");
        }
        return new SimulationSchedule(scheduleId, seed, interval, maxConcurrent,
                exceptionRatio, startedBy.trim(), startedAt);
    }

    /**
     * 止める（US36 §受入基準 4）。
     *
     * <p><b>すぐに停止中にしない。</b> 走っている実行は最後まで終える。
     * ここで停止中にすると、画面が「止まった」と出しているあいだ実行が続く。</p>
     */
    public void stop(Instant at) {
        if (!status.acceptsNewRuns()) {
            throw new IllegalTransition("稼働は" + status.label() + "です");
        }
        this.status = ScheduleStatus.STOPPING;
        this.stoppedAt = at;
    }

    /**
     * 走っている実行が全部決着したら停止中にする（US36 §受入基準 4）。
     *
     * @param running いま走っている実行の本数
     */
    public void settleIfDrained(int running, Instant at) {
        if (status == ScheduleStatus.STOPPING && running == 0) {
            this.status = ScheduleStatus.STOPPED;
            this.stoppedAt = at;
        }
    }

    /**
     * もう 1 本始めてよいか（US36 §受入基準 2）。
     *
     * @param running いま走っている実行の本数
     */
    public boolean canStartAnother(int running) {
        // **「以下」ではなく「未満」。** 取り違えると上限より 1 本多く走る。
        return status.acceptsNewRuns() && running < maxConcurrent;
    }

    /** 次に流す条件（US36 §受入基準 1）。<b>種から決まる</b>。 */
    public ScenarioInput nextScenario() {
        return random.next();
    }

    public String scheduleId() {
        return scheduleId;
    }

    public long seed() {
        return seed;
    }

    public Duration interval() {
        return interval;
    }

    public int maxConcurrent() {
        return maxConcurrent;
    }

    public BigDecimal exceptionRatio() {
        return exceptionRatio;
    }

    public ScheduleStatus status() {
        return status;
    }

    public String startedBy() {
        return startedBy;
    }

    public Instant startedAt() {
        return startedAt;
    }

    public Instant stoppedAt() {
        return stoppedAt;
    }
}
