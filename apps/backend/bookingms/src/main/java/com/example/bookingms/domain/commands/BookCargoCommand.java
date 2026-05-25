package com.example.bookingms.domain.commands;

import com.example.bookingms.domain.model.CargoSpecification;
import com.example.bookingms.domain.model.RouteSpecification;
import org.axonframework.modelling.command.TargetAggregateIdentifier;

/**
 * 貨物予約登録コマンド（US04）。
 *
 * <p>{@code bookingId} で識別される予約を新規作成する。</p>
 */
public record BookCargoCommand(
        @TargetAggregateIdentifier String bookingId,
        String shipperId,
        RouteSpecification routeSpec,
        CargoSpecification cargoSpec
) {
}
