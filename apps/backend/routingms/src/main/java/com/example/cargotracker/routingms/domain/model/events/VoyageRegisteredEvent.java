package com.example.cargotracker.routingms.domain.model.events;

import com.example.cargotracker.routingms.domain.model.valueobjects.Carrier;
import com.example.cargotracker.routingms.domain.model.valueobjects.CargoType;
import com.example.cargotracker.routingms.domain.model.valueobjects.CarrierMovement;
import com.example.cargotracker.routingms.domain.model.valueobjects.UnLocode;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 航海スケジュール登録完了イベント（US24）。
 *
 * <p>Voyage Aggregate が {@code RegisterVoyageCommand} を処理して発行する。
 * VoyageProjectionsEventHandler が routing_read_db に投影する。</p>
 */
public record VoyageRegisteredEvent(
        String voyageNumber,
        Carrier carrier,
        String shipName,
        UnLocode origin,
        UnLocode destination,
        LocalDateTime departureDate,
        LocalDateTime arrivalDate,
        List<CarrierMovement> carrierMovements,
        List<CargoType> acceptedCargoTypes) {
}
