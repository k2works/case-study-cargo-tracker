package com.example.bookingms.domain.model;

import com.example.bookingms.domain.commands.AssignRouteToCargoCommand;
import com.example.bookingms.domain.commands.AssignTrackingDetailsCommand;
import com.example.bookingms.domain.commands.BookCargoCommand;
import com.example.bookingms.domain.commands.CancelBookingCommand;
import com.example.bookingms.domain.commands.ConfirmBookingCommand;
import com.example.bookingms.domain.commands.MarkBookingSettledCommand;
import com.example.bookingms.domain.commands.NotifyRouteToShipperCommand;
import com.example.bookingms.domain.commands.RequestRouteDesignCommand;
import com.example.bookingms.domain.events.BookingCancelledEvent;
import com.example.bookingms.domain.events.BookingConfirmedEvent;
import com.example.bookingms.domain.events.BookingSettledEvent;
import com.example.bookingms.domain.events.CargoBookedEvent;
import com.example.bookingms.domain.events.CargoRoutedEvent;
import com.example.bookingms.domain.events.CargoTrackingAssignedEvent;
import com.example.bookingms.domain.events.RouteNotifiedToShipperEvent;
import com.example.shared.events.RouteDesignRequestedEvent;
import org.axonframework.commandhandling.CommandHandler;
import org.axonframework.eventsourcing.EventSourcingHandler;
import org.axonframework.modelling.command.AggregateIdentifier;
import org.axonframework.modelling.command.AggregateLifecycle;
import org.axonframework.spring.stereotype.Aggregate;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * 貨物予約集約（US04 + US05 / Booking Context）。
 *
 * <p>Event Sourcing で永続化される Aggregate Root。
 * 状態は CargoBookedEvent から再生される。
 * US05 で CargoType 別の固有情報（HazardInfo / TemperatureCondition）の検証を追加。</p>
 */
@Aggregate
public class Cargo {

    @AggregateIdentifier
    private String bookingId;
    @SuppressWarnings("unused") // Axon Event Sourcing で状態を保持するフィールド
    private String shipperId;
    private BookingStatus bookingStatus;
    @SuppressWarnings("unused") // Axon Event Sourcing で状態を保持するフィールド
    private RoutingStatus routingStatus;
    private RouteSpecification routeSpec;
    private CargoType cargoType;
    @SuppressWarnings("unused") // Axon Event Sourcing で状態を保持するフィールド
    private String trackingNumber;

    protected Cargo() {
        // Axon required no-arg constructor
    }

    @CommandHandler
    public Cargo(BookCargoCommand command) {
        validate(command);
        AggregateLifecycle.apply(new CargoBookedEvent(
                command.bookingId(),
                command.shipperId(),
                command.routeSpec(),
                command.cargoSpec(),
                BookingStatus.PRELIMINARY.name(),
                RoutingStatus.NOT_ROUTED.name()
        ));
    }

    private void validate(BookCargoCommand command) {
        if (command.bookingId() == null || command.bookingId().isBlank()) {
            throw new IllegalArgumentException("予約 ID は必須です");
        }
        if (command.shipperId() == null || command.shipperId().isBlank()) {
            throw new IllegalArgumentException("荷主 ID は必須です");
        }
        validateRouteSpec(command.routeSpec());
        validateCargoSpec(command.cargoSpec());
    }

    private void validateRouteSpec(RouteSpecification routeSpec) {
        if (routeSpec == null) {
            throw new IllegalArgumentException("経路仕様は必須です");
        }
        if (routeSpec.originUnlocode() == null || routeSpec.originUnlocode().isBlank()) {
            throw new IllegalArgumentException("出発地は必須です");
        }
        if (routeSpec.destinationUnlocode() == null || routeSpec.destinationUnlocode().isBlank()) {
            throw new IllegalArgumentException("目的地は必須です");
        }
        if (routeSpec.originUnlocode().equals(routeSpec.destinationUnlocode())) {
            throw new IllegalArgumentException("出発地と目的地は異なる必要があります");
        }
        if (routeSpec.arrivalDeadline() == null) {
            throw new IllegalArgumentException("到着期限は必須です");
        }
        if (routeSpec.arrivalDeadline().isBefore(LocalDate.now())) {
            throw new IllegalArgumentException("到着期限は今日以降である必要があります");
        }
    }

    private void validateCargoSpec(CargoSpecification cargoSpec) {
        if (cargoSpec == null) {
            throw new IllegalArgumentException("貨物仕様は必須です");
        }
        if (cargoSpec.cargoType() == null) {
            throw new IllegalArgumentException("貨物種別は必須です");
        }
        if (cargoSpec.weightKg() == null || cargoSpec.weightKg().compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("重量は 0 より大きい必要があります");
        }
        if (cargoSpec.quantity() <= 0) {
            throw new IllegalArgumentException("数量は 0 より大きい必要があります");
        }
        if (cargoSpec.productName() == null || cargoSpec.productName().isBlank()) {
            throw new IllegalArgumentException("品名は必須です");
        }
        validateCargoTypeSpecificInfo(cargoSpec);
    }

    private void validateCargoTypeSpecificInfo(CargoSpecification cargoSpec) {
        switch (cargoSpec.cargoType()) {
            case HAZARDOUS -> validateHazardInfo(cargoSpec);
            case REFRIGERATED -> validateTemperatureCondition(cargoSpec);
            case GENERAL -> validateNoSpecificInfo(cargoSpec);
        }
    }

    private void validateHazardInfo(CargoSpecification cargoSpec) {
        HazardInfo hazardInfo = cargoSpec.hazardInfo();
        if (hazardInfo == null) {
            throw new IllegalArgumentException("危険物貨物では危険物申告情報が必須です");
        }
        if (hazardInfo.imoClass() == null || hazardInfo.imoClass().isBlank()) {
            throw new IllegalArgumentException("危険物の IMO 分類クラスは必須です");
        }
        if (hazardInfo.unNumber() == null || hazardInfo.unNumber().isBlank()) {
            throw new IllegalArgumentException("危険物の国連番号は必須です");
        }
        if (hazardInfo.declaration() == null || hazardInfo.declaration().isBlank()) {
            throw new IllegalArgumentException("危険物の申告文は必須です");
        }
        if (cargoSpec.temperatureCondition() != null) {
            throw new IllegalArgumentException("危険物貨物に温度管理条件は指定できません");
        }
    }

    private void validateTemperatureCondition(CargoSpecification cargoSpec) {
        TemperatureCondition condition = cargoSpec.temperatureCondition();
        if (condition == null) {
            throw new IllegalArgumentException("冷凍貨物では温度管理条件が必須です");
        }
        if (condition.minCelsius() == null || condition.maxCelsius() == null) {
            throw new IllegalArgumentException("温度管理条件の最低・最高温度は必須です");
        }
        if (condition.minCelsius().compareTo(condition.maxCelsius()) > 0) {
            throw new IllegalArgumentException("温度管理条件の最低温度は最高温度以下である必要があります");
        }
        if (cargoSpec.hazardInfo() != null) {
            throw new IllegalArgumentException("冷凍貨物に危険物申告情報は指定できません");
        }
    }

    private void validateNoSpecificInfo(CargoSpecification cargoSpec) {
        if (cargoSpec.hazardInfo() != null) {
            throw new IllegalArgumentException("一般貨物に危険物申告情報は指定できません");
        }
        if (cargoSpec.temperatureCondition() != null) {
            throw new IllegalArgumentException("一般貨物に温度管理条件は指定できません");
        }
    }

    @EventSourcingHandler
    public void on(CargoBookedEvent event) {
        this.bookingId = event.bookingId();
        this.shipperId = event.shipperId();
        this.bookingStatus = BookingStatus.valueOf(event.bookingStatus());
        this.routingStatus = RoutingStatus.valueOf(event.routingStatus());
        this.routeSpec = event.routeSpec();
        this.cargoType = event.cargoSpec().cargoType();
    }

    /**
     * 経路設計引き渡し（US06）。仮受付（PRELIMINARY）のときのみ ROUTING へ遷移できる。
     */
    @CommandHandler
    public void handle(RequestRouteDesignCommand command) {
        if (this.bookingStatus != BookingStatus.PRELIMINARY) {
            throw new IllegalStateException("経路設計を依頼できるのは仮受付状態の予約のみです");
        }
        AggregateLifecycle.apply(new RouteDesignRequestedEvent(
                this.bookingId,
                BookingStatus.ROUTING.name(),
                this.routeSpec.originUnlocode(),
                this.routeSpec.destinationUnlocode(),
                this.routeSpec.arrivalDeadline(),
                this.cargoType.name()));
    }

    @EventSourcingHandler
    public void on(RouteDesignRequestedEvent event) {
        this.bookingStatus = BookingStatus.valueOf(event.bookingStatus());
    }

    /**
     * 経路割当（US11）。経路設計中（ROUTING）の予約に確定旅程を割り当て、経路提案中（ROUTE_PROPOSED）へ遷移する。
     * 旅程は予約の出発地・目的地に一致し、最終到着日が到着期限以内であること。
     */
    @CommandHandler
    public void handle(AssignRouteToCargoCommand command) {
        if (this.bookingStatus != BookingStatus.ROUTING) {
            throw new IllegalStateException("経路を割り当てできるのは経路設計中の予約のみです");
        }
        validateItinerary(command.legs());
        AggregateLifecycle.apply(new CargoRoutedEvent(
                this.bookingId,
                BookingStatus.ROUTE_PROPOSED.name(),
                RoutingStatus.ROUTED.name(),
                command.legs()));
    }

    private void validateItinerary(List<Leg> legs) {
        if (legs == null || legs.isEmpty()) {
            throw new IllegalArgumentException("経路（旅程）は 1 区間以上必要です");
        }
        Leg first = legs.get(0);
        Leg last = legs.get(legs.size() - 1);
        if (!this.routeSpec.originUnlocode().equals(first.loadUnlocode())) {
            throw new IllegalArgumentException("経路の出発地は予約の出発地と一致する必要があります");
        }
        if (!this.routeSpec.destinationUnlocode().equals(last.unloadUnlocode())) {
            throw new IllegalArgumentException("経路の最終到着地は予約の目的地と一致する必要があります");
        }
        if (last.unloadTime().toLocalDate().isAfter(this.routeSpec.arrivalDeadline())) {
            throw new IllegalArgumentException("経路の最終到着日は到着期限以内である必要があります");
        }
    }

    @EventSourcingHandler
    public void on(CargoRoutedEvent event) {
        this.bookingStatus = BookingStatus.valueOf(event.bookingStatus());
        this.routingStatus = RoutingStatus.valueOf(event.routingStatus());
    }

    /**
     * 確定経路の荷主通知（US12）。経路提案中（ROUTE_PROPOSED）のときのみ通知でき、状態は変更しない。
     */
    @CommandHandler
    public void handle(NotifyRouteToShipperCommand command) {
        if (this.bookingStatus != BookingStatus.ROUTE_PROPOSED) {
            throw new IllegalStateException("経路を荷主に通知できるのは経路提案中の予約のみです");
        }
        AggregateLifecycle.apply(new RouteNotifiedToShipperEvent(this.bookingId));
    }

    /**
     * 予約確定（US13）。経路提案中（ROUTE_PROPOSED）のときのみ CONFIRMED へ遷移できる。
     */
    @CommandHandler
    public void handle(ConfirmBookingCommand command) {
        if (this.bookingStatus != BookingStatus.ROUTE_PROPOSED) {
            throw new IllegalStateException("予約を確定できるのは経路提案中の予約のみです");
        }
        AggregateLifecycle.apply(new BookingConfirmedEvent(this.bookingId, BookingStatus.CONFIRMED.name()));
    }

    /**
     * 追跡情報割当（US14 / IT5 1.4）。予約確定（CONFIRMED）のときのみ TRACKING_ISSUED へ遷移する。
     *
     * <p>{@code BookingSagaManager} が trackingms から
     * {@code com.example.shared.events.CargoTrackedEvent}（採番完了通知）を受信した直後に発行する。
     * 採番された追跡番号を集約状態に保持し、{@link CargoTrackingAssignedEvent} で投影更新トリガーとなる。</p>
     */
    @CommandHandler
    public void handle(AssignTrackingDetailsCommand command) {
        if (this.bookingStatus != BookingStatus.CONFIRMED) {
            throw new IllegalStateException(
                    "追跡情報を割当できるのは予約確定（CONFIRMED）の予約のみです: 現状態=" + this.bookingStatus);
        }
        AggregateLifecycle.apply(new CargoTrackingAssignedEvent(
                this.bookingId, command.trackingNumber(), BookingStatus.TRACKING_ISSUED.name()));
    }

    /**
     * 予約キャンセル（US13）。確定済み・キャンセル済み以外であればキャンセルできる。
     */
    @CommandHandler
    public void handle(CancelBookingCommand command) {
        if (this.bookingStatus == BookingStatus.CONFIRMED || this.bookingStatus == BookingStatus.CANCELLED) {
            throw new IllegalStateException("確定済み・キャンセル済みの予約はキャンセルできません");
        }
        AggregateLifecycle.apply(new BookingCancelledEvent(this.bookingId, BookingStatus.CANCELLED.name()));
    }

    @EventSourcingHandler
    public void on(BookingConfirmedEvent event) {
        this.bookingStatus = BookingStatus.valueOf(event.bookingStatus());
    }

    @EventSourcingHandler
    public void on(CargoTrackingAssignedEvent event) {
        this.trackingNumber = event.trackingNumber();
        this.bookingStatus = BookingStatus.valueOf(event.bookingStatus());
    }

    /**
     * 予約「精算済」遷移（US23、IT7 T4.5、cross-service）。
     *
     * <p>billingms の {@code PaymentRecordedEvent} 経由で発火される。冪等化のため
     * 既に {@code SETTLED} / {@code CANCELLED} 状態の場合は静かに完了する（IllegalState ではない）。</p>
     */
    @CommandHandler
    public void handle(MarkBookingSettledCommand command) {
        if (this.bookingStatus == BookingStatus.SETTLED
                || this.bookingStatus == BookingStatus.CANCELLED) {
            // 冪等スキップ（重複配信・tracking 再処理対策）
            return;
        }
        if (this.bookingStatus != BookingStatus.TRACKING_ISSUED
                && this.bookingStatus != BookingStatus.IN_TRANSIT
                && this.bookingStatus != BookingStatus.DELIVERED
                && this.bookingStatus != BookingStatus.CONFIRMED) {
            throw new IllegalStateException(
                    "予約を精算済にできるのは CONFIRMED 以降の状態のみです: 現状態=" + this.bookingStatus);
        }
        AggregateLifecycle.apply(new BookingSettledEvent(this.bookingId, BookingStatus.SETTLED.name()));
    }

    @EventSourcingHandler
    public void on(BookingSettledEvent event) {
        this.bookingStatus = BookingStatus.valueOf(event.bookingStatus());
    }

    @EventSourcingHandler
    public void on(BookingCancelledEvent event) {
        this.bookingStatus = BookingStatus.valueOf(event.bookingStatus());
    }

    public String getBookingId() { return bookingId; }
}
