package com.example.routingms.application.internal;

import com.example.routingms.domain.model.CargoType;
import com.example.routingms.domain.model.Schedule;
import com.example.routingms.domain.model.VoyageNumber;
import java.util.Set;

/** 航海スケジュールの登録・更新の入力（US24・US25）。 */
public record RegisterVoyageCommand(
        VoyageNumber voyageNumber,
        String vesselName,
        String carrierName,
        Set<CargoType> supportedCargoTypes,
        Schedule schedule) {
}
