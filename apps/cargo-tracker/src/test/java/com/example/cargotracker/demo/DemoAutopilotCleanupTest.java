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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * 自動実行が作ったデータを<strong>まとめて片付けられる</strong>ことを確かめる。
 *
 * <p><strong>「消えたこと」だけでは足りない。</strong> 消しすぎていないことを同時に
 * 確かめる。片付けが起動時の投入データ（マニュアルの図と対応する固定のデータ）を
 * 巻き込むと、<strong>開発環境を作り直すまでマニュアルと画面が食い違い続ける</strong>。
 */
class DemoAutopilotCleanupTest extends PostgreSQLIntegrationTestBase {

    private static final Duration TIMEOUT = Duration.ofSeconds(60);

    @Autowired
    private DemoAutopilotService autopilot;

    @Autowired
    private DemoAutopilotCleanup cleanup;

    @Autowired
    private RegisterShipperCommandService registerShipper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    /**
     * <strong>印の無い荷主を 1 件用意する。</strong> 片付けが「消しすぎていない」ことを
     * 確かめるには、消してはならないものが実際に居なければならない。
     *
     * <p><strong>起動時の投入（{@code DemoDataInstaller}）は呼ばない。</strong>
     * あれは見積や便まで作り、<strong>実 DB を共有している他の検査の前提を壊す</strong>
     * （見積一覧の 0 件を確かめる検査が実際に落ちた）。自動実行は自分の便を作るため、
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

    @Test
    void 自動実行が作ったものだけをまとめて消す() {
        DemoAutopilotRun run = runToCompletion();
        int installedBefore = installedShippers();
        assertThat(installedBefore).as("起動時の投入データがある").isPositive();
        assertThat(cleanup.pending()).as("片付けの対象がある").isPositive();

        cleanup.reset();

        assertThat(markedShippers()).as("印の付いた荷主は消える").isZero();
        assertThat(cargoCount(run.bookingId())).as("その荷主の貨物も消える").isZero();
        assertThat(invoiceCount(run.bookingId())).as("請求書も消える").isZero();
        assertThat(trackingCount(run.bookingId())).as("追跡の記録も消える").isZero();
        assertThat(handlingCount(run.bookingId())).as("荷役の記録も消える").isZero();
        assertThat(installedShippers())
                .as("**起動時に投入した荷主は残る**（マニュアルの図と対応している）")
                .isEqualTo(installedBefore);
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

    /** <strong>片付けの前後で件数を画面に出せる。</strong> 何件消えるか分からないまま押させない。 */
    @Test
    void 片付けの対象件数を数えられる() {
        int before = cleanup.pending();

        runToCompletion();

        assertThat(cleanup.pending()).as("実行のたびに 1 件増える").isEqualTo(before + 1);
    }

    private int installedShippers() {
        return count("SELECT COUNT(*) FROM shipper WHERE contract_number IS NULL"
                + " OR contract_number NOT LIKE ?", DemoMark.CONTRACT_PREFIX + "%");
    }

    private int markedShippers() {
        return count("SELECT COUNT(*) FROM shipper WHERE contract_number LIKE ?",
                DemoMark.CONTRACT_PREFIX + "%");
    }

    private int cargoCount(String bookingId) {
        return count("SELECT COUNT(*) FROM cargo WHERE booking_id = CAST(? AS UUID)", bookingId);
    }

    private int invoiceCount(String bookingId) {
        return count("SELECT COUNT(*) FROM invoice WHERE booking_id = CAST(? AS UUID)", bookingId);
    }

    private int trackingCount(String bookingId) {
        return count("SELECT COUNT(*) FROM tracking_activity WHERE booking_id = CAST(? AS UUID)",
                bookingId);
    }

    private int handlingCount(String bookingId) {
        return count("SELECT COUNT(*) FROM handling_activity WHERE booking_id = CAST(? AS UUID)",
                bookingId);
    }

    private int count(String sql, Object... args) {
        Integer count = jdbcTemplate.queryForObject(sql, Integer.class, args);
        return count == null ? 0 : count;
    }

    private DemoAutopilotRun runToCompletion() {
        DemoAutopilotRun run = autopilot.start();
        Instant deadline = Instant.now().plus(TIMEOUT);
        while (run.state() == DemoAutopilotRun.State.RUNNING) {
            if (Instant.now().isAfter(deadline)) {
                throw new AssertionError("自動実行が %s 以内に終わりませんでした".formatted(TIMEOUT));
            }
            try {
                Thread.sleep(20);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("待機を中断しました", e);
            }
        }
        if (run.state() != DemoAutopilotRun.State.COMPLETED) {
            throw new AssertionError("自動実行が止まりました: " + run.failureReason());
        }
        return run;
    }
}
