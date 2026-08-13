package com.example.cargotracker.support;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.cargotracker.billing.application.internal.queryservices.BillingQueryService;
import com.example.cargotracker.booking.application.internal.queryservices.BookingQueryService;
import com.example.cargotracker.demo.DemoDataInstaller;
import com.example.cargotracker.estimation.application.internal.queryservices.EstimateQueryService;
import com.example.cargotracker.handling.application.internal.queryservices.HandlingQueryService;
import com.example.cargotracker.shared.application.paging.PageRequest;
import com.example.cargotracker.tracking.application.internal.queryservices.TrackingExceptionQueryService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * 開発環境の動作確認用データが<strong>ユーザーマニュアルと同じ状態</strong>になることを確かめる。
 *
 * <p><strong>マニュアルの図と開発環境の画面が食い違うと、読者はどちらが正しいか
 * 判断できない。</strong> {@code db/demo} の SQL は荷主・予約・航海までしか作れず、
 * 追跡・荷役・請求・見積の章はすべて空の画面になっていた。
 *
 * <p><strong>SQL では作れない。</strong> 追跡の記録も請求書も、荷役を登録した結果として
 * ドメインイベント（{@code AFTER_COMMIT}）が作る派生データである。SQL で
 * {@code handling_activity} だけを入れると、<strong>一覧には出るのに追跡画面と
 * 状態バッジが食い違う</strong>状態になる。採番（追跡番号・請求書番号）も
 * シーケンスと衝突する。
 *
 * <p>そのため投入は<strong>本番と同じ経路</strong>（アプリケーションサービス）で行う。
 *
 * <p><strong>この検査は「画面が到達しうる状態か」も同時に守る。</strong> 直接 INSERT で
 * 作った状態は、業務上あり得ない組み合わせでも作れてしまう。
 */
class DemoDataInstallerTest extends PostgreSQLIntegrationTestBase {

    @Autowired
    private DemoDataInstaller installer;

    @Autowired
    private BookingQueryService bookings;

    @Autowired
    private HandlingQueryService handling;

    @Autowired
    private com.example.cargotracker.booking.application.internal.queryservices
            .CancellationQueryService cancellations;

    @Autowired
    private TrackingExceptionQueryService exceptions;

    @Autowired
    private BillingQueryService billing;

    @Autowired
    private EstimateQueryService estimates;

    @BeforeEach
    void install() {
        installer.install();
    }

    @Test
    void 追跡管理の章が空にならない() {
        // 07-1 追跡番号の発行待ち
        assertThat(bookings.findAwaitingTracking(PageRequest.of(1)).items())
                .as("追跡番号の発行待ち（マニュアル 07.2）")
                .isNotEmpty();
        // 07-2 輸送中の貨物
        assertThat(bookings.findInTransit(PageRequest.of(1)).items())
                .as("輸送中の貨物（マニュアル 07.3）")
                .isNotEmpty();
    }

    @Test
    void 例外の一覧とエスカレーションが空にならない() {
        assertThat(exceptions.search(true, false))
                .as("未解決の例外（マニュアル 07.4）")
                .isNotEmpty();
        assertThat(exceptions.search(true, true))
                .as("エスカレーション済みの例外（マニュアル 07.4）")
                .isNotEmpty();
    }

    @Test
    void 承認待ちのキャンセルが残っている() {
        assertThat(cancellations.findPending())
                .as("承認待ちのキャンセル申請（マニュアル 07.6）")
                .isNotEmpty();
    }

    @Test
    void 荷役管理の章が空にならない() {
        assertThat(handling.findRecent(20))
                .as("荷役作業の一覧（マニュアル 08.1）")
                .isNotEmpty();
        assertThat(handling.findPendingDischarges())
                .as("荷降し手配の待ち（マニュアル 08.5）")
                .isNotEmpty();
    }

    @Test
    void 請求管理の章が空にならない() {
        assertThat(billing.findPendingCargo())
                .as("請求対象の貨物（マニュアル 11.1）")
                .isNotEmpty();
        assertThat(billing.findInvoices(
                        new com.example.cargotracker.billing.application.internal.queryservices
                                .InvoiceSearchCriteria(null, null, false, null, null, null)))
                .as("請求書の一覧（マニュアル 11.3）")
                .isNotEmpty();
    }

    @Test
    void 見積管理の章が空にならない() {
        assertThat(estimates.findAll())
                .as("見積の一覧（マニュアル 12.1）")
                .hasSizeGreaterThanOrEqualTo(2);
    }

    /**
     * <strong>2 回起動しても増えない。</strong> 開発環境は何度も起動する。
     * 起動のたびに予約が増えると、マニュアルの図と件数が合わなくなる。
     */
    @Test
    void 二度実行しても増えない() {
        int before = estimates.findAll().size();
        installer.install();
        assertThat(estimates.findAll()).hasSize(before);
    }
}
