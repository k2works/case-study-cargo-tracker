package com.example.cargotracker.bookingms.domain.model.events;

import com.example.cargotracker.bookingms.domain.model.valueobjects.CargoSpecification;
import com.example.cargotracker.bookingms.domain.model.valueobjects.RouteSpecification;
import com.example.cargotracker.bookingms.domain.model.valueobjects.ShipperId;
import org.axonframework.eventsourcing.annotation.EventTag;

/**
 * 貨物予約登録完了イベント（US04）。
 *
 * <p>Cargo Aggregate が {@code BookCargoCommand} を処理して発行する。
 * Event Store に永続化され、CargoProjectionsEventHandler が
 * cargo_summary Read Model を更新する。</p>
 *
 * <p>{@code @EventTag} により {@code bookingId} を tag として記録し、
 * {@code Cargo} 集約の Event Sourcing 再生時に同一 {@code bookingId} の
 * イベント列として識別可能にする（Axon 5 DCB）。</p>
 */
public record CargoBookedEvent(
        @EventTag String bookingId,
        ShipperId shipperId,
        CargoSpecification cargoSpec,
        RouteSpecification routeSpec) {
}
