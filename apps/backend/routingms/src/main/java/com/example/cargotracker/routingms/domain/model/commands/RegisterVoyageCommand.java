package com.example.cargotracker.routingms.domain.model.commands;

import com.example.cargotracker.routingms.domain.model.valueobjects.Carrier;
import com.example.cargotracker.routingms.domain.model.valueobjects.CargoType;
import com.example.cargotracker.routingms.domain.model.valueobjects.CarrierMovement;
import com.example.cargotracker.routingms.domain.model.valueobjects.UnLocode;
import org.axonframework.modelling.annotation.TargetEntityId;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;

/**
 * 航海スケジュール新規登録コマンド（US24）。
 *
 * <p>不変条件:</p>
 * <ul>
 *   <li>arrivalDate > departureDate（日付整合性）</li>
 *   <li>carrierMovements は 1 件以上</li>
 *   <li>origin != destination</li>
 * </ul>
 */
public record RegisterVoyageCommand(
        @TargetEntityId String voyageNumber,
        Carrier carrier,
        String shipName,
        UnLocode origin,
        UnLocode destination,
        LocalDateTime departureDate,
        LocalDateTime arrivalDate,
        List<CarrierMovement> carrierMovements,
        List<CargoType> acceptedCargoTypes) {

    public RegisterVoyageCommand {
        Objects.requireNonNull(voyageNumber, "voyageNumber");
        Objects.requireNonNull(carrier, "carrier");
        Objects.requireNonNull(shipName, "shipName");
        Objects.requireNonNull(origin, "origin");
        Objects.requireNonNull(destination, "destination");
        Objects.requireNonNull(departureDate, "departureDate");
        Objects.requireNonNull(arrivalDate, "arrivalDate");
        Objects.requireNonNull(carrierMovements, "carrierMovements");
        Objects.requireNonNull(acceptedCargoTypes, "acceptedCargoTypes");
        if (shipName.isBlank()) {
            throw new IllegalArgumentException("shipName は必須です");
        }
        if (origin.equals(destination)) {
            throw new IllegalArgumentException("origin と destination は同一にできません: " + origin.value());
        }
        if (!arrivalDate.isAfter(departureDate)) {
            throw new IllegalArgumentException("arrivalDate は departureDate より後である必要があります");
        }
        if (carrierMovements.isEmpty()) {
            throw new IllegalArgumentException("carrierMovements は 1 件以上必要です");
        }
    }
}
