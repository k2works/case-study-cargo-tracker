package com.example.cargotracker.routingms.domain.model.events;

import com.example.cargotracker.routingms.domain.model.valueobjects.CargoType;
import com.example.cargotracker.routingms.domain.model.valueobjects.CarrierMovement;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 航海スケジュール更新完了イベント（US25 / UC19）。
 *
 * <p>{@code Voyage} Aggregate が {@code UpdateVoyageScheduleCommand} を処理して発行する。
 * {@code VoyageProjectionsEventHandler} が {@code routing_read_db} の
 * {@code voyage} / {@code carrier_movement} / {@code voyage_accepted_cargo_type}
 * を再投影する。</p>
 *
 * <p>{@code Carrier} と {@code shipName} は更新対象外なのでイベントにも含めない。</p>
 */
public record VoyageScheduleUpdatedEvent(
        String voyageNumber,
        LocalDateTime departureDate,
        LocalDateTime arrivalDate,
        List<CarrierMovement> carrierMovements,
        List<CargoType> acceptedCargoTypes) {
}
