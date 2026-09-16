package com.example.cargotracker.routing.domain.model.commands;

import com.example.cargotracker.routing.domain.model.valueobjects.CargoType;
import com.example.cargotracker.routing.domain.model.valueobjects.Carrier;
import com.example.cargotracker.routing.domain.model.valueobjects.Schedule;
import com.example.cargotracker.routing.domain.model.valueobjects.VesselName;
import java.util.Set;
import org.axonframework.modelling.annotation.TargetEntityId;

/**
 * 登録済みの航海スケジュールを更新する（UC19 / US25）。
 *
 * <p>運送会社が運航変更を発表したときに、登録済みの内容を最新に置き換える。
 * <b>差し替えであって部分更新ではない。</b> 寄港地は順序を持つ列なので、
 * 「3 区間目だけ差し替える」形にすると、前後の連結と時刻の検査が区間ごとに
 * 分かれて {@link Schedule} の不変条件 2 を守れない。</p>
 */
public record UpdateVoyageScheduleCommand(
        @TargetEntityId String voyageNumber,
        Carrier carrier,
        VesselName vesselName,
        Schedule schedule,
        Set<CargoType> acceptedCargoTypes,
        String updatedBy) {
}
