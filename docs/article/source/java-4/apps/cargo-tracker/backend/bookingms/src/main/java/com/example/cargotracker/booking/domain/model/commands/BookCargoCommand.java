package com.example.cargotracker.booking.domain.model.commands;

import com.example.cargotracker.booking.domain.model.valueobjects.CargoSpecification;
import com.example.cargotracker.booking.domain.model.valueobjects.RouteSpecification;
import org.axonframework.modelling.annotation.TargetEntityId;

/**
 * 貨物予約を受け付ける（UC03 / US04・US05）。
 *
 * <p>{@code @TargetEntityId} が要る。集約に「作る側」（static）と「既にある側」の
 * ハンドラが両方あると、Axon は後者のためにコマンドから集約を特定できなければならない。
 * 付け忘れると {@code EntityIdResolutionException: found no identifiers} で落ちる
 * （IT2 で実測）。</p>
 */
public record BookCargoCommand(
        @TargetEntityId String bookingId,
        String shipperId,
        CargoSpecification cargoSpecification,
        RouteSpecification routeSpecification,
        String bookedBy) {
}
