package com.example.cargotracker.bookingms.domain.model.commands;

import com.example.cargotracker.bookingms.domain.model.valueobjects.CargoSpecification;
import com.example.cargotracker.bookingms.domain.model.valueobjects.RouteSpecification;
import com.example.cargotracker.bookingms.domain.model.valueobjects.ShipperId;
import org.axonframework.modelling.annotation.TargetEntityId;

import java.util.Objects;

/**
 * 貨物予約登録コマンド（US04）。
 *
 * <p>Axon 5.1 新 API: {@code @TargetEntityId} で Aggregate を識別。
 * 詳細は ADR-0007「Axon 5.1 Event Sourcing API」を参照。</p>
 */
public record BookCargoCommand(
        @TargetEntityId String bookingId,
        ShipperId shipperId,
        CargoSpecification cargoSpec,
        RouteSpecification routeSpec) {

    public BookCargoCommand {
        Objects.requireNonNull(bookingId, "bookingId");
        Objects.requireNonNull(shipperId, "shipperId");
        Objects.requireNonNull(cargoSpec, "cargoSpec");
        Objects.requireNonNull(routeSpec, "routeSpec");
    }
}
