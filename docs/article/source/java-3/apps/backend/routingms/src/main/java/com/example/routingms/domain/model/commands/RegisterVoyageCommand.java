package com.example.routingms.domain.model.commands;

import com.example.routingms.domain.model.valueobjects.CargoType;
import com.example.routingms.domain.model.valueobjects.Schedule;
import com.example.routingms.domain.model.valueobjects.VoyageNumber;
import java.util.Set;

/** 航海スケジュールの登録・更新の入力（US24・US25）。 */
public record RegisterVoyageCommand(
        VoyageNumber voyageNumber,
        String vesselName,
        String carrierName,
        Set<CargoType> supportedCargoTypes,
        Schedule schedule) {
}
