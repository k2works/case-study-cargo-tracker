package com.example.cargotracker.bookingms.domain.model.events;

import com.example.cargotracker.bookingms.domain.model.valueobjects.CargoSpecification;
import com.example.cargotracker.bookingms.domain.model.valueobjects.RouteSpecification;
import com.example.cargotracker.bookingms.domain.model.valueobjects.ShipperId;

/**
 * 貨物予約登録完了イベント（US04）。
 *
 * <p>Cargo Aggregate が {@code BookCargoCommand} を処理して発行する。
 * Event Store に永続化され、CargoProjectionsEventHandler が
 * cargo_summary Read Model を更新する。</p>
 */
public record CargoBookedEvent(
        String bookingId,
        ShipperId shipperId,
        CargoSpecification cargoSpec,
        RouteSpecification routeSpec) {
}
