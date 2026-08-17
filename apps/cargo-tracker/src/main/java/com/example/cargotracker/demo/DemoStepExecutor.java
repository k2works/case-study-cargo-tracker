package com.example.cargotracker.demo;

import com.example.cargotracker.handling.domain.model.valueobjects.HandlingType;
import com.example.cargotracker.routing.domain.model.valueobjects.RoutingCargoType;
import java.util.Set;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * 手順を 1 つだけ実行する。
 *
 * <p><strong>ここには業務ロジックが無い。</strong> 起動時の投入が使うのと
 * <strong>同じ {@code Demo*Steps}</strong> を、手順に応じて呼び分けるだけである。
 * 呼ぶサービスと順番が投入側と同じであることを、この形で担保している。
 *
 * <p><strong>結果を貨物に書き戻す。</strong> 追跡番号や便は後続の手順が使う。
 */
@ConditionalOnProperty(name = "cargo-tracker.demo.install", havingValue = "true")
@Component
class DemoStepExecutor {

    /**
     * 便が出港するまでの日数。<strong>今日より後でなければ選べない。</strong>
     */
    private static final int VOYAGE_DEPARTS_IN_DAYS = 3;

    /**
     * 便が到着するまでの日数。
     *
     * <p><strong>到着期限より前でなければ経路候補にならない</strong>
     * （{@code DemoScenario} が作る期限は 45 日以降）。
     */
    private static final int VOYAGE_ARRIVES_IN_DAYS = 20;

    private final DemoVoyageSteps voyages;
    private final DemoCorporateShipperSteps shippers;
    private final DemoBookingSteps bookings;
    private final DemoHandlingSteps handling;
    private final DemoAfterShipmentSteps afterShipment;

    DemoStepExecutor(
            DemoVoyageSteps voyages,
            DemoCorporateShipperSteps shippers,
            DemoBookingSteps bookings,
            DemoHandlingSteps handling,
            DemoAfterShipmentSteps afterShipment) {
        this.voyages = voyages;
        this.shippers = shippers;
        this.bookings = bookings;
        this.handling = handling;
        this.afterShipment = afterShipment;
    }

    /**
     * 手順ごとの実行内容。
     *
     * <p><strong>1 つの分岐に全部を書かない。</strong> 手順が 16 ある switch は
     * それだけで検査の上限（循環的複雑度 10）を超え、<strong>手順を足すたびに
     * 読みにくくなる</strong>。手順と実行内容の対応表にすれば、増えても平らなままである。
     */
    private final java.util.Map<DemoStep, java.util.function.Consumer<DemoCargoRun>> steps =
            new java.util.EnumMap<>(DemoStep.class);

    /**
     * 対応表を組み立てる。
     *
     * <p><strong>すべての手順を埋める。</strong> 埋め忘れると、その手順で
     * 何も起きないまま次へ進む —— <strong>飛ばしたことに誰も気づかない</strong>。
     * 埋まっていることは {@code DemoStepExecutorTest} が確かめている。
     */
    @jakarta.annotation.PostConstruct
    void register() {
        steps.put(DemoStep.REGISTER_VOYAGE, cargo -> voyages.register(
                shipment(cargo).voyage(),
                Set.of(RoutingCargoType.valueOf(shipment(cargo).cargoType().name())),
                shipment(cargo).origin(), shipment(cargo).destination(),
                VOYAGE_DEPARTS_IN_DAYS, VOYAGE_ARRIVES_IN_DAYS));
        steps.put(DemoStep.REGISTER_SHIPPER,
                cargo -> cargo.shipperId(shippers.register(cargo.scenario())));
        steps.put(DemoStep.BOOK, cargo -> cargo.bookingId(bookings.booked(
                cargo.shipperId(), shipment(cargo).origin(), shipment(cargo).destination(),
                shipment(cargo).cargoType(), shipment(cargo).weightKg(),
                shipment(cargo).deadlineInDays(), DemoMark.AUTOPILOT_DESCRIPTION)));
        steps.put(DemoStep.ASSIGN_TO_ROUTING,
                cargo -> bookings.assignToRouting(cargo.bookingId()));
        steps.put(DemoStep.PROPOSE_ROUTES, cargo -> cargo.voyage(
                bookings.proposeRoutes(cargo.bookingId()).orElseThrow(() ->
                        new IllegalStateException("経路候補が 0 件でした（%s → %s）".formatted(
                                shipment(cargo).origin(), shipment(cargo).destination())))));
        steps.put(DemoStep.SELECT_ROUTE,
                cargo -> bookings.selectRoute(cargo.bookingId(), cargo.voyage()));
        steps.put(DemoStep.CONFIRM_BOOKING, cargo -> bookings.confirmBooking(cargo.bookingId()));
        steps.put(DemoStep.ISSUE_TRACKING_NUMBER,
                cargo -> cargo.trackingNumber(handling.issue(cargo.bookingId())));
        steps.put(DemoStep.REGISTER_CONSIGNEE, cargo -> handling.registerConsignee(
                cargo.bookingId(), DemoHandlingSteps.CONSIGNEE_NAME));
        steps.put(DemoStep.RECEIVE, cargo -> handling.work(
                cargo.trackingNumber(), HandlingType.RECEIVE, shipment(cargo).origin(), null));
        steps.put(DemoStep.LOAD, cargo -> handling.work(
                cargo.trackingNumber(), HandlingType.LOAD, shipment(cargo).origin(),
                cargo.voyage().value()));
        steps.put(DemoStep.UNLOAD, cargo -> handling.work(
                cargo.trackingNumber(), HandlingType.UNLOAD, shipment(cargo).destination(),
                cargo.voyage().value()));
        steps.put(DemoStep.CUSTOMS, cargo -> handling.work(
                cargo.trackingNumber(), HandlingType.CUSTOMS, shipment(cargo).destination(), null));
        steps.put(DemoStep.CLEAR_CUSTOMS, cargo -> handling.clearCustoms(cargo.trackingNumber()));
        steps.put(DemoStep.CLAIM, cargo -> handling.claim(
                cargo.bookingId(), cargo.trackingNumber(), shipment(cargo).destination(),
                DemoHandlingSteps.CONSIGNEE_NAME));
        steps.put(DemoStep.CALCULATE_CHARGE,
                cargo -> afterShipment.calculateCharge(cargo.bookingId()));
    }

    /**
     * その貨物の次の 1 手を実行する。
     *
     * <p><strong>拒まれたら例外になる</strong>（{@code Demo*Steps} の {@code require}）。
     * 呼ぶ側がそれを貨物の失敗として記録する。
     */
    void execute(DemoCargoRun cargo) {
        java.util.function.Consumer<DemoCargoRun> step = steps.get(cargo.nextStep());
        if (step == null) {
            // **黙って飛ばさない。** 対応表の埋め忘れは、何も起きないまま
            // 次の手順へ進む形で現れる
            throw new IllegalStateException("実行内容が登録されていない手順です: " + cargo.nextStep());
        }
        step.accept(cargo);
    }

    /**
     * 実行内容が登録されている手順。
     *
     * <p><strong>検査が突き合わせるために公開している。</strong> 実際に実行して
     * 確かめようとすると、業務のサービスが投げる例外と「登録されていない」ことを
     * 区別できず、<strong>手順によっては本物のデータを作ってしまう</strong>。
     */
    java.util.Set<DemoStep> registeredSteps() {
        return java.util.Collections.unmodifiableSet(steps.keySet());
    }

    private DemoScenario.Shipment shipment(DemoCargoRun cargo) {
        return cargo.scenario().shipment();
    }
}
