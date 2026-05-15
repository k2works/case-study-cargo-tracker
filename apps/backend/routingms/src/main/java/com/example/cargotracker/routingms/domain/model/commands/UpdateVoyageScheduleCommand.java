package com.example.cargotracker.routingms.domain.model.commands;

import com.example.cargotracker.routingms.domain.model.valueobjects.CargoType;
import com.example.cargotracker.routingms.domain.model.valueobjects.CarrierMovement;
import org.axonframework.modelling.annotation.TargetEntityId;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;

/**
 * 航海スケジュール更新コマンド（US25 / UC19）。
 *
 * <p>運送会社が運航変更を発表した場合に、登録済み {@code Voyage} の出発・到着日時、
 * 寄港地リスト、対応貨物種別を最新情報に上書きする。</p>
 *
 * <p>不変条件（ドメインモデル {@code Schedule.isInternallyConsistent()} 相当）:</p>
 * <ul>
 *   <li>{@code arrivalDate > departureDate}</li>
 *   <li>{@code carrierMovements} は 1 件以上</li>
 *   <li>寄港地連続性: {@code carrierMovements[i].arrival == carrierMovements[i+1].departure}</li>
 *   <li>各 {@code CarrierMovement} の {@code arrivalTime > departureTime} は VO 側で保証</li>
 * </ul>
 *
 * <p>{@code Carrier} と {@code shipName} は更新対象外（変更時は別 Command で扱う）。</p>
 */
public record UpdateVoyageScheduleCommand(
        @TargetEntityId String voyageNumber,
        LocalDateTime departureDate,
        LocalDateTime arrivalDate,
        List<CarrierMovement> carrierMovements,
        List<CargoType> acceptedCargoTypes) {

    public UpdateVoyageScheduleCommand {
        Objects.requireNonNull(voyageNumber, "voyageNumber");
        Objects.requireNonNull(departureDate, "departureDate");
        Objects.requireNonNull(arrivalDate, "arrivalDate");
        Objects.requireNonNull(carrierMovements, "carrierMovements");
        Objects.requireNonNull(acceptedCargoTypes, "acceptedCargoTypes");
        if (!arrivalDate.isAfter(departureDate)) {
            throw new IllegalArgumentException("arrivalDate は departureDate より後である必要があります");
        }
        if (carrierMovements.isEmpty()) {
            throw new IllegalArgumentException("carrierMovements は 1 件以上必要です");
        }
        // 寄港地連続性検証: 前の到着港と次の出発港が一致する必要がある
        for (int i = 0; i < carrierMovements.size() - 1; i++) {
            var current = carrierMovements.get(i);
            var next = carrierMovements.get(i + 1);
            if (!current.arrival().equals(next.departure())) {
                throw new IllegalArgumentException(
                        "寄港地の連続性違反: movement[%d].arrival=%s != movement[%d].departure=%s"
                                .formatted(i, current.arrival().value(),
                                        i + 1, next.departure().value()));
            }
        }
    }
}
