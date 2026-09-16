package com.example.cargotracker.booking.domain.model.commands;

import com.example.cargotracker.booking.domain.model.valueobjects.CargoSpecification;
import com.example.cargotracker.booking.domain.model.valueobjects.RouteSpecification;
import org.axonframework.modelling.annotation.TargetEntityId;

/**
 * 仮受付の予約情報を修正する（UC03・UC04 / US32）。
 *
 * <p><b>経路条件の調整（US10 の {@code adjustRouteSpecification}）とは別物である。</b>
 * あちらは経路設計者が候補を出し直すために条件を動かすもので、こちらは営業が入力の
 * 誤りを直すもの。名前が近いので、取り違えないようコマンド名を分けている。</p>
 *
 * <p>荷主は変えられない（不変条件 1）。荷主を間違えたなら、それは別の予約である。</p>
 */
public record UpdateCargoSpecificationCommand(
        @TargetEntityId String bookingId,
        CargoSpecification cargoSpecification,
        RouteSpecification routeSpecification,
        String updatedBy) {
}
