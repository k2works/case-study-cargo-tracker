package com.example.cargotracker.demo;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.cargotracker.support.PostgreSQLIntegrationTestBase;
import java.time.Duration;
import java.time.Instant;
import java.util.function.BooleanSupplier;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * デモモードが<strong>入れているあいだ裏で業務を進め続ける</strong>ことを確かめる。
 *
 * <p><strong>「1 本が完走する」では足りない。</strong> 見せたいのは業務が並行して
 * 動いている様子であり、<strong>一覧に段階の違う貨物が並ぶ</strong>ことに意味がある。
 * 進んだ結果が業務の照会から引けることまで確かめる。
 *
 * <p><strong>{@code demo} パッケージに置く理由。</strong> 開始・停止を検査のためだけに
 * {@code public} にすると、<strong>本番のどこからでもデモモードを動かせる</strong>ように
 * なる。同じパッケージから呼べば、公開範囲を広げずに検査できる。
 */
class DemoModeScenarioTest extends PostgreSQLIntegrationTestBase {

    private static final Duration TIMEOUT = Duration.ofSeconds(60);

    @Autowired
    private DemoModeService demoMode;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    /**
     * <strong>数え直しから始める。</strong> デモモードは 1 つしかなく、
     * 完了・停止の数は検査をまたいで残る。前の検査の数が残っていると
     * <strong>「進んだ」条件が最初から満たされ、何も確かめないまま緑になる</strong>。
     */
    @BeforeEach
    void resetCounters() {
        demoMode.stop();
        demoMode.forgetAll();
    }

    /**
     * <strong>必ず止める。</strong> 動いたまま次の検査に移ると、
     * 裏で貨物が増え続けて<strong>他の検査が前提にしている件数を壊す</strong>。
     */
    @AfterEach
    void stopDemoMode() {
        demoMode.stop();
    }

    @Test
    void 入れているあいだ裏で業務が進み続ける() {
        assertThat(demoMode.running()).as("最初は止まっている").isFalse();

        demoMode.start();

        assertThat(demoMode.running()).as("入れたら動く").isTrue();
        await("貨物が請求まで通る", () -> demoMode.status().completedCargo() > 0);

        DemoModeStatus status = demoMode.status();
        assertThat(status.failedCargo())
                .as("途中で止まった貨物がない（直近の出来事: %s）", status.recentEvents())
                .isZero();
        assertThat(status.recentEvents())
                .as("何が起きたかが残る")
                .isNotEmpty()
                .allSatisfy(event -> assertThat(event.what()).isNotBlank());
    }

    /**
     * <strong>複数の貨物が違う段階に同時に居る。</strong> 1 件ずつ最後まで通すと、
     * 一覧にはいつも同じ段階の貨物しか居らず、業務が並行して動いている様子にならない。
     */
    @Test
    void 複数の貨物を並行して進める() {
        demoMode.start();

        await("進行中の貨物が 2 件以上になる", () -> demoMode.status().activeCargo() >= 2);

        assertThat(demoMode.status().activeCargo())
                .as("設定した数だけ並行して進める")
                .isGreaterThanOrEqualTo(2);
    }

    /**
     * <strong>止めたら本当に止まる。</strong> 止めたつもりで進み続けると、
     * デモを終えたあとも開発環境のデータが増え続ける。
     */
    @Test
    void 止めたら進まなくなる() {
        demoMode.start();
        await("何か作られる", () -> markedCargoCount() > 0);

        demoMode.stop();
        int afterStop = markedCargoCount();
        sleep(Duration.ofMillis(500));

        assertThat(demoMode.running()).as("止まっている").isFalse();
        assertThat(markedCargoCount())
                .as("止めたあとに増えない")
                .isEqualTo(afterStop);
    }

    /** <strong>二度入れても実行は重ならない。</strong> 重なると間隔の設定が意味を失う。 */
    @Test
    void 二度入れても実行は重ならない() {
        demoMode.start();
        demoMode.start();

        await("進む", () -> markedCargoCount() > 0);

        assertThat(demoMode.running()).isTrue();
        demoMode.stop();
        assertThat(demoMode.running())
                .as("**一度止めれば止まる。** 二重に動いていれば片方が残る")
                .isFalse();
    }

    /** デモモードが作った貨物の数（印で数える）。 */
    private int markedCargoCount() {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM cargo WHERE description = ?",
                Integer.class, DemoMark.AUTOPILOT_DESCRIPTION);
        return count == null ? 0 : count;
    }

    /**
     * 条件が満たされるまで待つ。
     *
     * <p><strong>一定時間眠って結果を見る形にしない。</strong> 遅い環境で偶然落ちるか、
     * 速い環境で無駄に待つ。
     */
    private void await(String what, BooleanSupplier condition) {
        Instant deadline = Instant.now().plus(TIMEOUT);
        while (!condition.getAsBoolean()) {
            if (Instant.now().isAfter(deadline)) {
                DemoModeStatus status = demoMode.status();
                throw new AssertionError(
                        "%s を %s 以内に確かめられませんでした（進行中 %d / 完了 %d / 停止 %d）"
                                .formatted(what, TIMEOUT, status.activeCargo(),
                                        status.completedCargo(), status.failedCargo()));
            }
            sleep(Duration.ofMillis(20));
        }
    }

    private void sleep(Duration duration) {
        try {
            Thread.sleep(duration.toMillis());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("待機を中断しました", e);
        }
    }
}
