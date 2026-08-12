package com.example.cargotracker.demo;

import com.example.cargotracker.booking.domain.model.valueobjects.BookingId;
import com.example.cargotracker.booking.domain.model.valueobjects.CargoType;
import com.example.cargotracker.tracking.domain.model.valueobjects.ExceptionType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * 開発環境の動作確認用データを、<strong>ユーザーマニュアルと同じ状態</strong>まで作る。
 *
 * <p><strong>マニュアルの図と開発環境の画面が食い違うと、読者はどちらが正しいか
 * 判断できない。</strong> {@code db/demo} の SQL は荷主・予約・航海までしか作れず、
 * 追跡・荷役・請求・見積の章はすべて空の画面だった。
 *
 * <p><strong>なぜ SQL で作らないか。</strong>
 *
 * <ul>
 *   <li>追跡の記録・貨物の状態・キャンセル料の請求書は、荷役を登録した結果として
 *       ドメインイベント（{@code AFTER_COMMIT}）が作る<strong>派生データ</strong>である。
 *       {@code handling_activity} だけを INSERT すると、<strong>一覧には出るのに
 *       追跡画面と状態バッジが食い違う</strong></li>
 *   <li>追跡番号と請求書番号は<strong>シーケンスで採番</strong>している。直接 INSERT すると
 *       画面からの初回発行が採番済みの値を返し、一意制約で 500 になる
 *       （{@code shipper_code_seq} で実際に踏み、{@code V905} で RESTART している）</li>
 *   <li>途中の状態を直接作ると、<strong>画面が実際に到達しうる状態かを確かめないまま</strong>
 *       図を作ることになる</li>
 * </ul>
 *
 * <p>そのため投入は<strong>本番と同じ経路</strong>（アプリケーションサービス）で行う。
 *
 * <p><strong>BC をまたぐのが仕事である。</strong> 本クラスは合成ルートと同じ立場にあり、
 * どの BC にも属さない（{@code PackageStructureTest} で除外している）。業務ロジックは
 * 一切持たず、<strong>画面と同じ順番で手順を並べるだけ</strong>である。
 *
 * <p><strong>何度起動しても増えない。</strong> 開発環境は繰り返し起動する。
 * 起動のたびに予約が増えると、マニュアルの図と件数が合わなくなる。
 */
@ConditionalOnProperty(name = "cargo-tracker.demo.install", havingValue = "true")
@Component
public class DemoDataInstaller {

    private static final Logger LOG = LoggerFactory.getLogger(DemoDataInstaller.class);

    private final DemoVoyageSteps voyages;
    private final DemoEstimateSteps estimates;
    private final DemoBookingSteps bookings;
    private final DemoHandlingSteps handling;
    private final DemoAfterShipmentSteps afterShipment;
    private final DemoCancellationSteps cancellations;
    private final DemoInstallMarker marker;

    DemoDataInstaller(
            DemoVoyageSteps voyages,
            DemoEstimateSteps estimates,
            DemoBookingSteps bookings,
            DemoHandlingSteps handling,
            DemoAfterShipmentSteps afterShipment,
            DemoCancellationSteps cancellations,
            DemoInstallMarker marker) {
        this.voyages = voyages;
        this.estimates = estimates;
        this.bookings = bookings;
        this.handling = handling;
        this.afterShipment = afterShipment;
        this.cancellations = cancellations;
        this.marker = marker;
    }

    /**
     * マニュアルの各章が「データのある状態」で開けるところまで作る。
     *
     * <p><strong>途中で失敗しても起動は止めない。</strong> 動作確認用データであり、
     * これが無くてもアプリケーションは動く。ただし<strong>黙って飛ばさない</strong> ——
     * どの章で止まったかをログに残す。
     */
    public void install() {
        if (marker.alreadyInstalled()) {
            LOG.info("動作確認用データは投入済みです");
            return;
        }
        // **途中で失敗したら、そこで止める。**
        //
        // 以前は失敗した章だけを飛ばして先へ進んでいた。だが投入済みの印は
        // 「1 件でも作られたか」であり、**次の起動では「投入済み」と見なされて
        // 残りの章が永久に空のまま**になる。ログの警告は前回の起動にしか残らない
        // ため、翌日には手掛かりも消える。
        if (!step("航路（マニュアル 05）", voyages::install)
                || !step("見積（マニュアル 12）", estimates::install)
                || !step("追跡番号の発行待ち（マニュアル 07.2）", this::installAwaitingTracking)
                || !step("輸送中と荷役（マニュアル 07.3 / 08.1）", this::installInTransit)
                || !step("例外とエスカレーション（マニュアル 07.4）", this::installExceptions)
                || !step("荷降し手配（マニュアル 08.8）", this::installDischargeOrder)
                || !step("請求（マニュアル 11）", this::installBilling)) {
            LOG.error("動作確認用データの投入を中断しました。**途中まで作られています。**"
                    + " このまま再起動しても投入済みと見なされ、残りの章は空のままです。"
                    + " 作り直すにはデータベースを作り直してください"
                    + "（local は H2 のため再起動で消えます）。");
            return;
        }
        LOG.info("動作確認用データを投入しました（マニュアルと同じ状態です）");
    }

    /** @return 作れたか。<strong>作れなければ以降は進めない</strong> */
    private boolean step(String name, Runnable work) {
        try {
            work.run();
            return true;
        } catch (RuntimeException e) {
            // **黙って飛ばさない。** 章が空のまま起動すると、
            // マニュアルと画面の食い違いに気づけない
            LOG.warn("動作確認用データを作れませんでした: {} ({})", name, e.getMessage());
            return false;
        }
    }

    /** 確定済みで追跡番号がまだ無い予約（マニュアル 07.2）。 */
    private void installAwaitingTracking() {
        bookings.confirmed(DemoVoyageSteps.KOBE, DemoVoyageSteps.ROTTERDAM, CargoType.GENERAL, "2400", 60);
    }

    /** 輸送中の貨物と荷役の記録（マニュアル 07.3 / 08.1）。 */
    private void installInTransit() {
        BookingId id = bookings.confirmed(DemoVoyageSteps.OSAKA, DemoVoyageSteps.LOS_ANGELES, CargoType.GENERAL, "1800", 55);
        handling.receiveAndLoad(handling.issue(id), DemoVoyageSteps.OSAKA, DemoVoyageSteps.DIRECT_VOYAGE);
    }

    /** 未解決の遅延と、エスカレーション済みの紛失を 1 件ずつ（マニュアル 07.4）。 */
    private void installExceptions() {
        String delayed = handling.issue(
                bookings.confirmed(DemoVoyageSteps.OSAKA, DemoVoyageSteps.LOS_ANGELES, CargoType.GENERAL, "900", 50));
        handling.receive(delayed, DemoVoyageSteps.OSAKA);
        afterShipment.raise(delayed, ExceptionType.DELAY, DemoVoyageSteps.OSAKA,
                "港湾の混雑により出港が遅れています");

        String lost = handling.issue(
                bookings.confirmed(DemoVoyageSteps.OSAKA, DemoVoyageSteps.LOS_ANGELES, CargoType.GENERAL, "600", 50));
        handling.receive(lost, DemoVoyageSteps.OSAKA);
        afterShipment.raise(lost, ExceptionType.LOST, DemoVoyageSteps.OSAKA, "積込後に所在が確認できません");
    }

    /**
     * キャンセルを 2 件作る（マニュアル 07.6 / 08.5）。
     *
     * <p><strong>承認済みだけでは足りない。</strong> 追跡管理者が最初に開くのは
     * <strong>承認待ちの一覧</strong>であり、そこが空だと何も判断できない。
     */
    private void installDischargeOrder() {
        BookingId approved = bookings.confirmed(DemoVoyageSteps.OSAKA, DemoVoyageSteps.LOS_ANGELES, CargoType.GENERAL, "1500", 50);
        handling.receiveAndLoad(handling.issue(approved), DemoVoyageSteps.OSAKA, DemoVoyageSteps.DIRECT_VOYAGE);
        cancellations.requestAndApprove(approved);

        // **承認待ちのまま残す 1 件**（マニュアル 07.6）
        BookingId pending = bookings.confirmed(DemoVoyageSteps.OSAKA, DemoVoyageSteps.LOS_ANGELES, CargoType.GENERAL, "1100", 48);
        handling.receiveAndLoad(handling.issue(pending), DemoVoyageSteps.OSAKA, DemoVoyageSteps.DIRECT_VOYAGE);
        cancellations.request(pending);
    }

    /**
     * 引取まで済ませた貨物を 2 件作る（マニュアル 11）。
     *
     * <p><strong>1 件は請求書まで進め、1 件は未請求のまま残す。</strong>
     * 請求対象の一覧は「まだ請求書の無い貨物」であり、
     * <strong>全部を請求済みにすると一覧が空になる</strong>。
     */
    private void installBilling() {
        afterShipment.calculateCharge(delivered("3000", 45));
        // **未請求のまま残す 1 件**（マニュアル 11.1 の一覧）
        delivered("2200", 40);
    }

    private BookingId delivered(String weight, int days) {
        BookingId id = bookings.confirmed(DemoVoyageSteps.OSAKA, DemoVoyageSteps.LOS_ANGELES, CargoType.GENERAL, weight, days);
        handling.deliver(id, handling.issue(id), DemoVoyageSteps.DIRECT_VOYAGE,
                DemoVoyageSteps.OSAKA, DemoVoyageSteps.LOS_ANGELES);
        return id;
    }
}
