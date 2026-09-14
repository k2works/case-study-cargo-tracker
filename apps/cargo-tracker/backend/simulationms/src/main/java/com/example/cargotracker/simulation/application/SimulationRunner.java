package com.example.cargotracker.simulation.application;

import com.example.cargotracker.simulation.domain.model.aggregates.SimulationRun;
import com.example.cargotracker.simulation.domain.model.valueobjects.StepKind;
import java.time.Clock;
import java.time.Duration;
import java.util.EnumMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 工程を順に実行する（US33 §受入基準 1・6 / US34 §受入基準 1・3）。
 *
 * <p><b>止まったら、そこで終える。</b> 以降の工程を実行しないのは、失敗したあとに
 * 業務データを増やさないためである。<b>それまでに作られたものは取り消さない</b>
 * ——どこまで進んだかを追えることが US34 の目的である。</p>
 */
public class SimulationRunner {

    private static final Logger log = LoggerFactory.getLogger(SimulationRunner.class);

    /**
     * 追いつくまで読み直す回数。
     *
     * <p><b>読み直す間隔を長くしない。</b> 連鎖はふつう数百ミリ秒で追いつく。
     * 長い間隔にすると、追いついたのに待ち続けて実行が遅くなる。</p>
     */
    private static final int READ_ATTEMPTS = 60;

    /** 読み直す間隔。{@link #READ_ATTEMPTS} と掛けたものが待てる上限になる。 */
    private static final Duration READ_INTERVAL = Duration.ofMillis(500);

    private final BusinessApi api;
    private final ChainReadiness readiness;
    private final Sleeper sleeper;
    private final Clock clock;
    private final StepListener listener;

    public SimulationRunner(BusinessApi api, ChainReadiness readiness, Sleeper sleeper,
            Clock clock) {
        this(api, readiness, sleeper, clock, (run, step) -> { });
    }

    public SimulationRunner(BusinessApi api, ChainReadiness readiness, Sleeper sleeper,
            Clock clock, StepListener listener) {
        this.api = api;
        this.readiness = readiness;
        this.sleeper = sleeper;
        this.clock = clock;
        this.listener = listener;
    }

    /**
     * 工程を 1 件記録したときに呼ばれる。
     *
     * <p><b>終わってからまとめて書かない。</b> 標準シナリオは 13 工程あり、
     * 最後まで走るのに数十秒かかる——そのあいだ画面が何も出せないと、
     * 「どこまで進んだか」を見るための US34 が実行中には使えない。</p>
     */
    @FunctionalInterface
    public interface StepListener {

        /** 記録された。 */
        void recorded(SimulationRun run, SimulationRun.RecordedStep step);
    }

    /**
     * 眠り方。<b>検査では眠らない</b>——経過時間のアサートは脆弱な実装に戻しても
     * 緑になるので、待ちは「何回読み直したか」で見る（IT7 の教訓）。
     */
    @FunctionalInterface
    public interface Sleeper {

        /** その時間だけ待つ。 */
        void sleep(Duration duration);
    }

    /** 実際に眠る。本番はこれを使う。 */
    public static Sleeper realSleeper() {
        return duration -> {
            try {
                Thread.sleep(duration.toMillis());
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("待ちが中断されました", e);
            }
        };
    }

    /**
     * シナリオの工程を順に実行する。
     *
     * <p><b>投げっぱなしにしない。</b> 予期しない例外（接続できない等）も
     * 失敗した工程として記録する——実行が決着しないと、二重実行の守り
     * （実行中は 1 本だけ）がその実行を掴んだまま離さなくなる。</p>
     */
    public void run(SimulationRun run) {
        Map<StepKind, String> produced = new EnumMap<>(StepKind.class);

        for (StepKind kind : run.plannedSteps()) {
            long startedAtNanos = System.nanoTime();
            BusinessApi.StepResult result;
            try {
                result = api.execute(kind, Map.copyOf(produced));
            } catch (RuntimeException e) {
                log.warn("工程が例外で終わった: runId={} step={}", run.runId(), kind, e);
                result = BusinessApi.StepResult.failure(500, e.getMessage());
            }
            Duration elapsed = Duration.ofNanos(System.nanoTime() - startedAtNanos);

            if (!result.succeeded()) {
                run.recordFailure(kind, elapsed, result.failureStatus(),
                        result.failureMessage(), clock.instant());
                notifyLast(run);
                // **以降は実行しない。** 止まったあとに業務データを増やさない。
                return;
            }
            if (result.producedId() != null) {
                produced.put(kind, result.producedId());
            }
            // **連鎖の結果を待ってから次へ進む**（US33 §6）。待つのは成功した
            // 工程のあとだけ——止まったあとに読み口を叩いても意味が無い。
            if (!awaitChain(kind, produced)) {
                run.recordFailure(kind, elapsed, null,
                        "「" + kind.label() + "」の結果が読めるようになりませんでした（"
                                + READ_ATTEMPTS * READ_INTERVAL.toMillis() / 1000
                                + " 秒待ちました）。連鎖が止まっているか、"
                                + "待ちの宣言が実際の読み口と食い違っています",
                        clock.instant());
                notifyLast(run);
                return;
            }
            run.recordSuccess(kind, elapsed, result.producedId(), clock.instant());
            notifyLast(run);
        }
    }

    /**
     * 直前に記録した工程を知らせる。
     *
     * <p><b>知らせに失敗しても実行を落とさない。</b> 記録の書き出しが 1 度
     * 失敗しても、業務の連鎖そのものは進んでいる——落とすと、進んだのに
     * 「中断」として残る。</p>
     */
    private void notifyLast(SimulationRun run) {
        var steps = run.recordedSteps();
        try {
            listener.recorded(run, steps.get(steps.size() - 1));
        } catch (RuntimeException e) {
            log.warn("工程の書き出しに失敗した: runId={}", run.runId(), e);
        }
    }

    /**
     * 連鎖が追いつくまで読み直す。
     *
     * <p><b>読み口の例外は「まだ読めない」として扱う。</b> 投影が起き上がる途中の
     * 1 度の失敗で、実行ごと落とさない。</p>
     */
    private boolean awaitChain(StepKind kind, Map<StepKind, String> produced) {
        for (int attempt = 0; attempt < READ_ATTEMPTS; attempt++) {
            try {
                if (readiness.isReady(kind, Map.copyOf(produced))) {
                    return true;
                }
            } catch (RuntimeException e) {
                log.debug("読み口がまだ返さない: step={} attempt={}", kind, attempt, e);
            }
            sleeper.sleep(READ_INTERVAL);
        }
        return false;
    }
}
