package com.example.cargotracker.handlingms.domain.model.commands;

import com.example.cargotracker.handlingms.domain.model.valueobjects.CargoSnapshot;
import com.example.cargotracker.handlingms.domain.model.valueobjects.ClaimVerification;
import com.example.cargotracker.handlingms.domain.model.valueobjects.HandlerId;
import com.example.cargotracker.handlingms.domain.model.valueobjects.HandlingType;
import com.example.cargotracker.handlingms.domain.model.valueobjects.Location;
import com.example.cargotracker.handlingms.domain.model.valueobjects.TrackingNumber;
import com.example.cargotracker.handlingms.domain.model.valueobjects.VoyageNumber;
import java.time.LocalDateTime;
import java.util.Objects;
import org.axonframework.modelling.annotation.TargetEntityId;

/**
 * 荷役作業登録コマンド（US15 / US16）。
 *
 * <p>Axon 5.1 新 API: {@code @TargetEntityId} で Aggregate を識別。
 * 詳細は ADR-0007「Axon 5.1 Event Sourcing API」を参照。</p>
 *
 * <p>不変条件:</p>
 * <ul>
 *   <li>{@link HandlingType#LOAD} / {@link HandlingType#UNLOAD} の場合、{@code voyageNumber} は必須</li>
 *   <li>{@link HandlingType#CLAIM} の場合、{@code claimVerification} は必須（US16）</li>
 *   <li>{@code occurredAt} は過去または現在のみ</li>
 * </ul>
 *
 * @param activityId          荷役作業 ID（Aggregate Identifier）
 * @param trackingNumber      追跡番号（CargoSnapshot 引当用）
 * @param handlingType        作業種別
 * @param location            作業場所
 * @param occurredAt          作業発生日時
 * @param voyageNumber        航海番号（LOAD/UNLOAD 時必須）
 * @param operatorId          作業員 ID
 * @param claimVerification   引取確認（CLAIM 時必須）
 * @param cargoSnapshot       貨物スナップショット（ACL 経由で Controller が引当して渡す）
 */
public record RegisterHandlingActivityCommand(
        @TargetEntityId String activityId,
        TrackingNumber trackingNumber,
        HandlingType handlingType,
        Location location,
        LocalDateTime occurredAt,
        VoyageNumber voyageNumber,
        HandlerId operatorId,
        ClaimVerification claimVerification,
        CargoSnapshot cargoSnapshot) {

    public RegisterHandlingActivityCommand {
        Objects.requireNonNull(activityId, "activityId");
        Objects.requireNonNull(trackingNumber, "trackingNumber");
        Objects.requireNonNull(handlingType, "handlingType");
        Objects.requireNonNull(location, "location");
        Objects.requireNonNull(occurredAt, "occurredAt");
        Objects.requireNonNull(operatorId, "operatorId");
        Objects.requireNonNull(cargoSnapshot, "cargoSnapshot");
        if (handlingType.requiresVoyageNumber() && voyageNumber == null) {
            throw new IllegalArgumentException(
                    handlingType + " 種別の作業には voyageNumber が必須です");
        }
        if (handlingType.requiresClaimVerification() && claimVerification == null) {
            throw new IllegalArgumentException(
                    "CLAIM 種別の作業には claimVerification が必須です（US16）");
        }
    }
}
