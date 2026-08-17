package com.example.cargotracker.demo;

import com.example.cargotracker.booking.domain.model.valueobjects.BookingId;
import com.example.cargotracker.handling.domain.model.valueobjects.HandlingType;
import com.example.cargotracker.routing.domain.model.valueobjects.RoutingCargoType;
import com.example.cargotracker.shared.domain.model.valueobjects.ShipperId;
import java.time.Clock;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicLong;
import java.util.random.RandomGenerator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

/**
 * 法人荷主の登録から請求までを<strong>1 手ずつ、間を置いて</strong>実行する。
 *
 * <p><strong>起動時の投入（{@code DemoDataInstaller}）と何が違うか。</strong>
 * あちらは「マニュアルと同じ状態」を作るために一気に流す。こちらは
 * <strong>業務が進む様子そのものを見せる</strong>ためにあり、手順ごとに間を置いて
 * 進み具合を残す。呼ぶサービスと順番は同じであり、
 * <strong>同じ {@code Demo*Steps} を使う</strong>ことで 2 つの経路が食い違わないようにしている。
 *
 * <p><strong>要求を処理するスレッドで走らせない。</strong> 手順ごとに待つため、
 * 画面からの要求をそのまま実行に使うと<strong>ボタンを押した利用者が最後まで待たされる</strong>。
 * 別のスレッドで走らせ、画面は進み具合を読みに来る。
 *
 * <p><strong>作ったものは印付きである。</strong> 契約番号の接頭辞（{@code DemoMark}）を
 * 起点に、{@code DemoAutopilotCleanup} がまとめて片付けられる。
 */
@ConditionalOnProperty(name = "cargo-tracker.demo.install", havingValue = "true")
@Service
public class DemoAutopilotService {

    private static final Logger LOG = LoggerFactory.getLogger(DemoAutopilotService.class);

    /**
     * 手順の数。<strong>画面が進捗率を出すために、始まる前から分かっている必要がある。</strong>
     *
     * <p><strong>手で持つ値はずれる。</strong> {@link #execute} に手順を足したときに
     * ここを直し忘れると、進捗率が 100% を超えるか、最後まで進んでも埋まらない。
     * <strong>実際に流したときの手順数と一致することを検査で止めている</strong>
     * （{@code DemoAutopilotScenarioTest}）。
     */
    static final int STEP_COUNT = 16;

    /**
     * 便が出港するまでの日数。
     *
     * <p><strong>今日より後でなければならない。</strong> 出港済みの便は選べない。
     */
    private static final int VOYAGE_DEPARTS_IN_DAYS = 3;

    /**
     * 便が到着するまでの日数。
     *
     * <p><strong>到着期限（45 日以降）より前でなければ候補にならない</strong>
     * （{@code DemoScenario} の期限と揃えること）。
     */
    private static final int VOYAGE_ARRIVES_IN_DAYS = 20;

    private final DemoVoyageSteps voyages;
    private final DemoCorporateShipperSteps shippers;
    private final DemoBookingSteps bookings;
    private final DemoHandlingSteps handling;
    private final DemoAfterShipmentSteps afterShipment;
    private final DemoAutopilotProperties properties;
    private final Clock clock;

    /** 実行の履歴。<strong>新しいものが後ろに来る順で保つ。</strong> */
    private final Map<UUID, DemoAutopilotRun> runs = new LinkedHashMap<>();

    private final AtomicLong sequence = new AtomicLong(System.currentTimeMillis() % 100000);
    private final RandomGenerator random = RandomGenerator.getDefault();
    private final ExecutorService executor = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "demo-autopilot");
        // **アプリケーションの終了を妨げない。** 実行中に落としても待たせない
        thread.setDaemon(true);
        return thread;
    });

    DemoAutopilotService(
            DemoVoyageSteps voyages,
            DemoCorporateShipperSteps shippers,
            DemoBookingSteps bookings,
            DemoHandlingSteps handling,
            DemoAfterShipmentSteps afterShipment,
            DemoAutopilotProperties properties,
            Clock clock) {
        this.voyages = voyages;
        this.shippers = shippers;
        this.bookings = bookings;
        this.handling = handling;
        this.afterShipment = afterShipment;
        this.properties = properties;
        this.clock = clock;
    }

    /**
     * 新しい実行を始める。
     *
     * @return 始めた実行。<strong>すぐ返る</strong>（実行そのものは別スレッドで進む）
     */
    DemoAutopilotRun start() {
        DemoScenario scenario = DemoScenario.random(random, sequence.incrementAndGet());
        DemoAutopilotRun run = new DemoAutopilotRun(scenario, clock.instant());
        remember(run);
        executor.execute(() -> execute(run));
        return run;
    }

    /** 実行を 1 件引く。 */
    Optional<DemoAutopilotRun> find(UUID id) {
        synchronized (runs) {
            return Optional.ofNullable(runs.get(id));
        }
    }

    /** 実行の履歴を新しい順に返す。 */
    Collection<DemoAutopilotRun> recent() {
        synchronized (runs) {
            return runs.values().stream()
                    .sorted((a, b) -> b.startedAt().compareTo(a.startedAt()))
                    .toList();
        }
    }

    /** 履歴を空にする（片付けと合わせて呼ぶ）。 */
    void forgetAll() {
        synchronized (runs) {
            runs.clear();
        }
    }

    private void remember(DemoAutopilotRun run) {
        synchronized (runs) {
            runs.put(run.id(), run);
            // **際限なく覚えない。** 古いものから捨てる
            var iterator = runs.entrySet().iterator();
            while (runs.size() > properties.maxRuns() && iterator.hasNext()) {
                iterator.next();
                iterator.remove();
            }
        }
    }

    /**
     * 手順を順に実行する。
     *
     * <p><strong>1 手でも失敗したら、そこで止める。</strong> 業務の順番そのものが
     * ルールであり（通関が下りるまで引取は登録できない）、飛ばして先へ進むと
     * <strong>実際には到達しない状態を画面に見せる</strong>ことになる。
     */
    private void execute(DemoAutopilotRun run) {
        DemoScenario scenario = run.scenario();
        try {
            // **この貨物のための便を先に作る。** 起動時に投入した便に相乗りさせると、
            // 割り当てるたびに空き容量が減り、**繰り返すほど動かなくなる**
            step(run, "航海スケジュールを登録する", "経路設計者",
                    () -> {
                        voyages.register(
                                scenario.shipment().voyage(),
                                java.util.Set.of(RoutingCargoType.valueOf(
                                        scenario.shipment().cargoType().name())),
                                scenario.shipment().origin(), scenario.shipment().destination(),
                                VOYAGE_DEPARTS_IN_DAYS, VOYAGE_ARRIVES_IN_DAYS);
                        return null;
                    },
                    ignored -> "%s ／ %s → %s".formatted(
                            scenario.shipment().voyage(),
                            scenario.shipment().origin(), scenario.shipment().destination()));

            ShipperId shipper = step(run, "法人荷主を登録する", "営業担当者",
                    () -> shippers.register(scenario),
                    id -> "%s（契約 %s）".formatted(
                            scenario.corporate().name(), scenario.contractNumber()));

            BookingId booking = step(run, "貨物を予約する", "営業担当者",
                    () -> bookings.booked(
                            shipper, scenario.shipment().origin(), scenario.shipment().destination(),
                            scenario.shipment().cargoType(), scenario.shipment().weightKg(),
                            scenario.shipment().deadlineInDays(), DemoMark.AUTOPILOT_DESCRIPTION),
                    id -> "%s → %s ／ %s %skg".formatted(
                            scenario.shipment().origin(), scenario.shipment().destination(),
                            scenario.shipment().cargoType().displayName(), scenario.shipment().weightKg()));
            run.bookingId(booking.value().toString());

            step(run, "経路設計者に引き渡す", "営業担当者",
                    () -> {
                        bookings.assignToRouting(booking);
                        return null;
                    },
                    ignored -> "経路提案待ちになりました");

            // **候補 0 件は手順の中で弾く。** 手順の外で投げると、失敗が
            // どの手順のものか記録されず、**画面は実行中のまま止まる**
            var voyage = step(run, "経路候補を算出する", "経路設計者",
                    () -> bookings.proposeRoutes(booking).orElseThrow(() ->
                            new IllegalStateException(
                                    "経路候補が 0 件でした（%s → %s に乗れる便がありません）"
                                            .formatted(scenario.shipment().origin(),
                                                    scenario.shipment().destination()))),
                    found -> "候補から %s を選びます".formatted(found.value()));

            step(run, "経路を確定する", "経路設計者",
                    () -> {
                        bookings.selectRoute(booking, voyage);
                        return null;
                    },
                    ignored -> "%s に割り当てました".formatted(voyage.value()));

            step(run, "予約を確定する", "営業担当者",
                    () -> {
                        bookings.confirmBooking(booking);
                        return null;
                    },
                    ignored -> "確認済みになりました");

            String trackingNumber = step(run, "追跡番号を発行する", "追跡管理者",
                    () -> handling.issue(booking),
                    number -> number);
            run.trackingNumber(trackingNumber);

            step(run, "荷受人を登録する", "営業担当者",
                    () -> {
                        handling.registerConsignee(booking, DemoHandlingSteps.CONSIGNEE_NAME);
                        return null;
                    },
                    ignored -> DemoHandlingSteps.CONSIGNEE_NAME);

            work(run, booking, trackingNumber, HandlingType.RECEIVE,
                    scenario.shipment().origin(), null, "貨物を受け取る");
            work(run, booking, trackingNumber, HandlingType.LOAD,
                    scenario.shipment().origin(), voyage.value(), "船に積み込む");
            work(run, booking, trackingNumber, HandlingType.UNLOAD,
                    scenario.shipment().destination(), voyage.value(), "船から降ろす");
            work(run, booking, trackingNumber, HandlingType.CUSTOMS,
                    scenario.shipment().destination(), null, "通関に搬入する");

            step(run, "通関を通す", "荷役作業員",
                    () -> {
                        handling.clearCustoms(trackingNumber);
                        return null;
                    },
                    ignored -> "通関済みになりました");

            step(run, "荷受人が引き取る", "荷役作業員",
                    () -> {
                        handling.claim(booking, trackingNumber, scenario.shipment().destination(),
                                DemoHandlingSteps.CONSIGNEE_NAME);
                        return null;
                    },
                    ignored -> "引取完了になりました");

            step(run, "請求書を作成する", "経理担当者",
                    () -> {
                        afterShipment.calculateCharge(booking);
                        return null;
                    },
                    ignored -> "契約割引 %s%% を適用しました"
                            .formatted(scenario.discountPercentage()));

            run.complete();
            LOG.info("自動実行デモが完了しました（追跡番号 {}）", trackingNumber);
        } catch (DemoAutopilotStopped e) {
            // **止まったこと自体は記録済みである**（step が失敗を書いてから投げる）。
            // ここでは運用の手掛かりだけ残す
            LOG.warn("自動実行デモが途中で止まりました: {}", e.getMessage());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            LOG.info("自動実行デモを中断しました");
        }
    }

    /** 荷役を 1 件登録する手順。 */
    private void work(
            DemoAutopilotRun run, BookingId booking, String trackingNumber,
            HandlingType type, String location, String voyage, String title)
            throws InterruptedException {
        step(run, title, "荷役作業員",
                () -> {
                    handling.work(trackingNumber, type, location, voyage);
                    return null;
                },
                ignored -> "%s ／ %s".formatted(type.name(), location));
    }

    /**
     * 手順をひとつ実行する。
     *
     * <p><strong>始めたことを先に記録する。</strong> 実行してから記録すると、
     * 待っている間の画面に何も出ず、止まっているように見える。
     *
     * <p><strong>間を置くのは手順のあとである。</strong> 先に待つと、
     * ボタンを押してから最初の手順が出るまで無反応の時間ができる。
     */
    private <T> T step(
            DemoAutopilotRun run, String title, String actor,
            Work<T> work, Describe<T> describe) throws InterruptedException {
        DemoAutopilotRun.Step step = run.begin(title, actor, clock.instant());
        try {
            T result = work.run();
            run.succeed(step, describe.of(result));
            Thread.sleep(properties.stepInterval().toMillis());
            return result;
        } catch (InterruptedException e) {
            throw e;
        } catch (RuntimeException e) {
            String reason = e.getMessage() == null ? e.toString() : e.getMessage();
            run.fail(step, reason);
            // **記録してから、記録済みだと分かる型で投げる**（DemoAutopilotStopped）
            throw new DemoAutopilotStopped("%s で止まりました: %s".formatted(title, reason), e);
        }
    }

    /** 手順の中身。 */
    @FunctionalInterface
    private interface Work<T> {
        T run();
    }

    /** 結果を画面の言葉にする。 */
    @FunctionalInterface
    private interface Describe<T> {
        String of(T result);
    }
}
