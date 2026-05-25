package com.example.bookingms.domain.model;

import com.example.bookingms.domain.commands.BookCargoCommand;
import com.example.bookingms.domain.events.CargoBookedEvent;
import org.axonframework.commandhandling.CommandHandler;
import org.axonframework.eventsourcing.EventSourcingHandler;
import org.axonframework.modelling.command.AggregateIdentifier;
import org.axonframework.modelling.command.AggregateLifecycle;
import org.axonframework.spring.stereotype.Aggregate;

import java.math.BigDecimal;
import java.time.LocalDate;

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
    @SuppressWarnings("unused") // Axon Event Sourcing で状態を保持するフィールド
    private BookingStatus bookingStatus;
    @SuppressWarnings("unused") // Axon Event Sourcing で状態を保持するフィールド
    private RoutingStatus routingStatus;

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
    }

    public String getBookingId() { return bookingId; }
}
