package com.example.cargotracker.routing.domain.model.commands;

import com.example.cargotracker.routing.domain.model.valueobjects.Carrier;
import com.example.cargotracker.routing.domain.model.valueobjects.CargoType;
import com.example.cargotracker.routing.domain.model.valueobjects.Schedule;
import com.example.cargotracker.routing.domain.model.valueobjects.VesselName;
import java.util.Set;
import org.axonframework.modelling.annotation.TargetEntityId;

/**
 * 航海スケジュールを登録する（UC19 / US24）。
 *
 * <p>{@code @TargetEntityId} が要る。集約のコマンドハンドラはインスタンス側に置くので、
 * Axon はコマンドから集約を特定できなければならない。付け忘れると
 * {@code EntityIdResolutionException: found no identifiers} で落ちる（IT2 で実測）。</p>
 */
public record RegisterVoyageCommand(
        @TargetEntityId String voyageNumber,
        Carrier carrier,
        VesselName vesselName,
        Schedule schedule,
        Set<CargoType> acceptedCargoTypes,
        String registeredBy) {
}
