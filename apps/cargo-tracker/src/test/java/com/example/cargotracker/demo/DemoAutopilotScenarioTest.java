package com.example.cargotracker.demo;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.cargotracker.booking.application.internal.queryservices.BookingQueryService;
import com.example.cargotracker.support.PostgreSQLIntegrationTestBase;
import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * 自動実行デモが<strong>法人荷主の登録から請求書まで一本つながる</strong>ことを確かめる。
 *
 * <p><strong>「手順が完了と記録された」では足りない。</strong> それは記録の側が
 * 正しいだけかもしれない。予約・追跡番号・請求書が<strong>業務の照会から実際に引ける</strong>
 * ことまで確かめる。
 *
 * <p><strong>{@code demo} パッケージに置く理由。</strong> 実行の入口を検査のためだけに
 * {@code public} にすると、<strong>本番のどこからでも自動実行を始められる</strong>ように
 * なる。同じパッケージから呼べば、公開範囲を広げずに検査できる。
 */
class DemoAutopilotScenarioTest extends PostgreSQLIntegrationTestBase {

    /** 待ちの上限。<strong>手順の間隔はテストでは 0 である</strong>（application-test.yml）。 */
    private static final Duration TIMEOUT = Duration.ofSeconds(60);

    @Autowired
    private DemoAutopilotService autopilot;

    @Autowired
    private BookingQueryService bookings;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void 法人荷主の登録から請求書まで一本つながる() {
        DemoAutopilotRun run = runToCompletion();

        assertThat(run.state())
                .as("止まった理由: %s", run.failureReason())
                .isEqualTo(DemoAutopilotRun.State.COMPLETED);
        assertThat(run.steps())
                .as("すべての手順が完了になる")
                .isNotEmpty()
                .allSatisfy(step -> assertThat(step.state())
                        .isEqualTo(DemoAutopilotRun.StepState.DONE));

        // **記録だけでなく、業務の照会からも引けること**
        assertThat(bookings.findById(run.bookingId()))
                .as("予約が引ける")
                .isPresent();
        assertThat(run.trackingNumber())
                .as("追跡番号が発行されている")
                .startsWith("TRK-");
        assertThat(invoiceCount(run.bookingId()))
                .as("請求書が作られている")
                .isEqualTo(1);
    }

    /**
     * <strong>進捗率の分母がずれない。</strong> 手順を足して {@code STEP_COUNT} を
     * 直し忘れると、画面は最後まで進んでもバーが埋まらないか、100% を超える。
     */
    @Test
    void 手順の数が画面に出す総数と一致する() {
        DemoAutopilotRun run = runToCompletion();

        assertThat(run.steps())
                .as("実際に流した手順の数と、画面が分母に使う総数は同じでなければならない")
                .hasSize(run.totalSteps());
    }

    /**
     * <strong>作ったものには印が付いている。</strong> 印が無ければ後から片付けられず、
     * 実際の登録と見分けが付かない。
     */
    @Test
    void 作った荷主に片付け用の印が付く() {
        DemoAutopilotRun run = runToCompletion();

        Integer marked = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM shipper WHERE contract_number = ?",
                Integer.class, run.scenario().contractNumber());
        assertThat(marked).as("契約番号がそのまま登録されている").isEqualTo(1);
        assertThat(run.scenario().contractNumber())
                .as("片付けの起点になる印が付いている")
                .startsWith(DemoMark.CONTRACT_PREFIX);
    }

    /**
     * <strong>動かすたびに別の荷主になる。</strong> 登録サービスは同じ連絡先の荷主が
     * いれば<strong>登録せずに既存を返す</strong>。連絡先を固定すると、画面は
     * 「登録しました」と出しながら前回の荷主を使い回すことになる。
     */
    @Test
    void 動かすたびに別の法人荷主を登録する() {
        DemoAutopilotRun first = runToCompletion();
        DemoAutopilotRun second = runToCompletion();

        assertThat(first.scenario().contractNumber())
                .as("契約番号が毎回変わる")
                .isNotEqualTo(second.scenario().contractNumber());
        assertThat(first.bookingId())
                .as("別の予約になる")
                .isNotEqualTo(second.bookingId());
    }

    private int invoiceCount(String bookingId) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM invoice WHERE booking_id = CAST(? AS UUID)",
                Integer.class, bookingId);
        return count == null ? 0 : count;
    }

    /**
     * 実行が終わるまで待つ。
     *
     * <p><strong>終わったかどうかで待つ。</strong> 一定時間眠って結果を見る形にすると、
     * 遅い環境で偶然落ちるか、速い環境で無駄に待つ。
     */
    private DemoAutopilotRun runToCompletion() {
        DemoAutopilotRun run = autopilot.start();
        Instant deadline = Instant.now().plus(TIMEOUT);
        while (run.state() == DemoAutopilotRun.State.RUNNING) {
            if (Instant.now().isAfter(deadline)) {
                throw new AssertionError(
                        "自動実行が %s 以内に終わりませんでした（%d 手目まで進みました）"
                                .formatted(TIMEOUT, run.steps().size()));
            }
            sleep();
        }
        return run;
    }

    private void sleep() {
        try {
            Thread.sleep(20);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("待機を中断しました", e);
        }
    }
}
