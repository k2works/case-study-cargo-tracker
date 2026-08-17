package com.example.cargotracker.demo;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.cargotracker.shipper.application.internal.commandservices
        .RegisterShipperCommandService;
import com.example.cargotracker.shipper.domain.model.valueobjects.Address;
import com.example.cargotracker.shipper.domain.model.valueobjects.Email;
import com.example.cargotracker.shipper.domain.model.valueobjects.Phone;
import com.example.cargotracker.shipper.domain.model.valueobjects.ShipperName;
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
 * デモモードが作ったデータを<strong>まとめて片付けられる</strong>ことを確かめる。
 *
 * <p><strong>「消えたこと」だけでは足りない。</strong> 消しすぎていないことを同時に
 * 確かめる。片付けが実際の登録を巻き込むと、開発環境を作り直すまで戻らない。
 */
class DemoModeCleanupTest extends PostgreSQLIntegrationTestBase {

    private static final Duration TIMEOUT = Duration.ofSeconds(60);

    @Autowired
    private DemoModeService demoMode;

    @Autowired
    private DemoModeCleanup cleanup;

    @Autowired
    private RegisterShipperCommandService registerShipper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    /**
     * <strong>数え直しから始める。</strong> デモモードは 1 つしかなく、完了・停止の数は
     * 検査をまたいで残る。前の検査の数が残っていると<strong>「進んだ」条件が最初から
     * 満たされ、何も作られていないのに片付けを確かめたことになる</strong>（実際に踏んだ）。
     */
    @BeforeEach
    void resetCounters() {
        demoMode.stop();
        demoMode.forgetAll();
    }

    /**
     * <strong>印の無い荷主を 1 件用意する。</strong> 片付けが「消しすぎていない」ことを
     * 確かめるには、消してはならないものが実際に居なければならない。
     *
     * <p><strong>起動時の投入（{@code DemoDataInstaller}）は呼ばない。</strong>
     * あれは見積や便まで作り、<strong>実 DB を共有している他の検査の前提を壊す</strong>
     * （見積一覧の 0 件を確かめる検査が実際に落ちた）。デモモードは自分の便を作るため、
     * 投入に依存しない。
     */
    @BeforeEach
    void registerUnmarkedShipper() {
        registerShipper.register(
                new ShipperName("片付けで消えない荷主"),
                new Email("cleanup-guard@example.com"),
                new Phone("03-0000-0001"),
                new Address("JP", "100-0002", "東京都", "千代田区", "丸の内 1-1"));
    }

    @AfterEach
    void stopDemoMode() {
        demoMode.stop();
    }

    @Test
    void デモモードが作ったものだけをまとめて消す() {
        demoMode.start();
        await("請求まで通った貨物が出る", () -> demoMode.status().completedCargo() > 0);
        demoMode.stop();

        int unmarkedBefore = unmarkedShippers();
        assertThat(unmarkedBefore).as("消してはならない荷主が居る").isPositive();
        assertThat(cleanup.pending()).as("片付けの対象が居る").isPositive();

        cleanup.reset();

        assertThat(markedShippers()).as("印の付いた荷主は消える").isZero();
        assertThat(markedCargo()).as("その荷主の貨物も消える").isZero();
        assertThat(markedVoyages()).as("その貨物のために作った便も消える").isZero();
        assertThat(unmarkedShippers())
                .as("**印の無い荷主は残る**")
                .isEqualTo(unmarkedBefore);
    }

    /**
     * <strong>片付けは先に止める。</strong> 動いたまま消すと、消している間に
     * 次の 1 手が走り、消し残しができる。
     */
    @Test
    void 片付けても消し残しが出ない() {
        demoMode.start();
        await("何か作られる", () -> markedCargo() > 0);

        demoMode.stop();
        cleanup.reset();

        assertThat(markedCargo()).as("貨物が残らない").isZero();
        assertThat(markedShippers()).as("荷主が残らない").isZero();
    }

    /**
     * <strong>何も無いときに落ちない。</strong> 片付けは繰り返し押される。
     * 2 回目が例外になると、画面は壊れているようにしか見えない。
     */
    @Test
    void 対象が無くても片付けは通る() {
        cleanup.reset();

        assertThat(cleanup.reset()).as("2 回目は 0 件").isZero();
    }

    private int unmarkedShippers() {
        return count("SELECT COUNT(*) FROM shipper WHERE contract_number IS NULL"
                + " OR contract_number NOT LIKE ?", DemoMark.CONTRACT_PREFIX + "%");
    }

    private int markedShippers() {
        return count("SELECT COUNT(*) FROM shipper WHERE contract_number LIKE ?",
                DemoMark.CONTRACT_PREFIX + "%");
    }

    private int markedCargo() {
        return count("SELECT COUNT(*) FROM cargo WHERE description = ?",
                DemoMark.AUTOPILOT_DESCRIPTION);
    }

    private int markedVoyages() {
        return count("SELECT COUNT(*) FROM voyage WHERE voyage_number LIKE ?",
                DemoMark.CONTRACT_PREFIX + "%");
    }

    private int count(String sql, Object... args) {
        Integer count = jdbcTemplate.queryForObject(sql, Integer.class, args);
        return count == null ? 0 : count;
    }

    private void await(String what, BooleanSupplier condition) {
        Instant deadline = Instant.now().plus(TIMEOUT);
        while (!condition.getAsBoolean()) {
            if (Instant.now().isAfter(deadline)) {
                throw new AssertionError("%s を %s 以内に確かめられませんでした（停止 %d 件）"
                        .formatted(what, TIMEOUT, demoMode.status().failedCargo()));
            }
            try {
                Thread.sleep(20);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("待機を中断しました", e);
            }
        }
    }
}
