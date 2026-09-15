package com.example.cargotracker.simulation.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.cargotracker.simulation.domain.model.aggregates.SimulationRun;
import com.example.cargotracker.simulation.domain.model.valueobjects.RunStatus;
import com.example.cargotracker.simulation.domain.model.valueobjects.Scenario;
import com.example.cargotracker.simulation.domain.model.valueobjects.StepKind;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 連鎖の結果を待ってから次へ進む（US33 §受入基準 6）。
 *
 * <p><b>待ち時間で判別しない。</b> 7 サービスの結果整合なので、投影が追いつく前に
 * 次を叩くと**通っている経路が失敗として記録される**。かといって固定の秒数を
 * 眠ると、速い日は無駄に遅く、混んだ日は足りない。<b>読み口が返した値</b>で
 * 判別する。</p>
 *
 * <p><b>眠らない検査にする。</b> 経過時間のアサートは脆弱な実装に戻しても緑に
 * なる（IT7 の教訓）。ここでは「何回読み直したか」と「何を待っていたか」で見る。</p>
 */
class ChainWaitTest {

    private static final Instant NOW = Instant.parse("2026-09-14T01:00:00Z");
    private static final Clock CLOCK = Clock.fixed(NOW, ZoneId.of("Asia/Tokyo"));

    /** 眠らない。**待ちの回数だけ数える**。 */
    private static final class CountingSleeper implements SimulationRunner.Sleeper {
        private final AtomicInteger slept = new AtomicInteger();

        @Override
        public void sleep(Duration duration) {
            slept.incrementAndGet();
        }
    }

    private static SimulationRun standardRun() {
        return SimulationRun.start("run-1", Scenario.NO_ROUTE, null, "admin01", NOW);
    }

    @Test
    @DisplayName("US33 §6: 読めるようになるまで読み直してから次へ進む")
    void waitsUntilTheReadModelCatchesUp() {
        AtomicInteger reads = new AtomicInteger();
        // 3 回目の読み直しで追いつく。
        ChainReadiness readiness = (kind, produced) -> reads.incrementAndGet() >= 3;
        CountingSleeper sleeper = new CountingSleeper();
        SimulationRun run = standardRun();

        new SimulationRunner((kind, produced) -> BusinessApi.StepResult.success("id-" + kind),
                readiness, sleeper, CLOCK).run(run);

        assertThat(run.status()).isEqualTo(RunStatus.SUCCEEDED);
        assertThat(sleeper.slept.get())
                .as("**追いつくまで読み直す。** 1 度読んで進むと、通っている経路が失敗になる")
                .isPositive();
    }

    @Test
    @DisplayName("US33 §6: 追いつかなければ、何を待っていたかを言って失敗にする")
    void failsWithWhatItWasWaitingFor() {
        // いつまでも追いつかない。
        ChainReadiness never = (kind, produced) -> false;
        SimulationRun run = standardRun();

        new SimulationRunner((kind, produced) -> BusinessApi.StepResult.success("id"),
                never, new CountingSleeper(), CLOCK).run(run);

        assertThat(run.status()).isEqualTo(RunStatus.FAILED);
        var failed = run.recordedSteps().get(run.recordedSteps().size() - 1);
        assertThat(failed.failureMessage())
                .as("**「失敗しました」では切り分けられない。** 連鎖の欠落か待ち不足かを"
                        + "読む人が判断できる文言にする")
                .contains("読めるようになりませんでした")
                .contains(StepKind.REGISTER_SHIPPER.label());
    }

    @Test
    @DisplayName("N8: 連鎖の待ちを所要時間とは別に記録する（足し合わせない）")
    void recordsTheChainWaitSeparately() {
        AtomicInteger reads = new AtomicInteger();
        // 1 件目の工程だけ 3 回読み直してから追いつく（＝2 回眠る）。
        ChainReadiness readiness = (kind, produced) ->
                kind != StepKind.REGISTER_SHIPPER || reads.incrementAndGet() >= 3;
        SimulationRun run = standardRun();

        new SimulationRunner((kind, produced) -> BusinessApi.StepResult.success("id-" + kind),
                readiness, new CountingSleeper(), CLOCK).run(run);

        var first = run.recordedSteps().get(0);
        assertThat(first.waited())
                .as("**「13 工程が数ミリ秒ずつ」と読ませない。** 時間を使っているのは連鎖である")
                .isPositive();
        assertThat(first.elapsed())
                .as("**足し合わせない。** 呼び出しが遅いのか連鎖が遅いのかは、"
                        + "切り分けでいちばん知りたい区別である")
                .isNotEqualTo(first.waited());
        assertThat(run.recordedSteps().get(1).waited())
                .as("待たずに通った工程は 0（「分からない」と混ぜない）")
                .isZero();
    }

    @Test
    @DisplayName("待つのは成功した工程のあとだけ（失敗したら待たない）")
    void doesNotWaitAfterAFailedStep() {
        AtomicInteger checked = new AtomicInteger();
        ChainReadiness readiness = (kind, produced) -> {
            checked.incrementAndGet();
            return true;
        };
        SimulationRun run = standardRun();

        new SimulationRunner((kind, produced) -> BusinessApi.StepResult.failure(500, "落ちた"),
                readiness, new CountingSleeper(), CLOCK).run(run);

        assertThat(run.status()).isEqualTo(RunStatus.FAILED);
        assertThat(checked.get())
                .as("止まったあとに読み口を叩かない")
                .isZero();
    }

    @Test
    @DisplayName("待ちは工程ごとに宣言される（読み口を知らない工程は待たない）")
    void onlyWaitsForStepsThatDeclareIt() {
        List<StepKind> waited = new ArrayList<>();
        ChainReadiness readiness = (kind, produced) -> {
            waited.add(kind);
            return true;
        };
        SimulationRun run = standardRun();

        new SimulationRunner((kind, produced) -> BusinessApi.StepResult.success(null),
                readiness, new CountingSleeper(), CLOCK).run(run);

        assertThat(waited)
                .as("**どの工程で何を待つか**は宣言で決まる（実行が推測しない）")
                .isEqualTo(Scenario.NO_ROUTE.steps());
    }

    @Test
    @DisplayName("読み口が例外を投げても、追いついていないものとして扱う（投げっぱなしにしない）")
    void treatsAReadErrorAsNotReadyYet() {
        AtomicInteger reads = new AtomicInteger();
        ChainReadiness flaky = (kind, produced) -> {
            if (reads.incrementAndGet() < 2) {
                throw new IllegalStateException("まだ読めない");
            }
            return true;
        };
        SimulationRun run = standardRun();

        new SimulationRunner((kind, produced) -> BusinessApi.StepResult.success(null),
                flaky, new CountingSleeper(), CLOCK).run(run);

        assertThat(run.status())
                .as("投影が起き上がる途中の 1 度の失敗で、実行ごと落とさない")
                .isEqualTo(RunStatus.SUCCEEDED);
    }
}
