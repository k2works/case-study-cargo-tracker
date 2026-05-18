package com.example.cargotracker.handlingms.domain.model.events;

import com.example.cargotracker.handlingms.domain.model.valueobjects.CargoSnapshot;
import com.example.cargotracker.handlingms.domain.model.valueobjects.ClaimVerification;
import com.example.cargotracker.handlingms.domain.model.valueobjects.HandlerId;
import com.example.cargotracker.handlingms.domain.model.valueobjects.HandlingType;
import com.example.cargotracker.handlingms.domain.model.valueobjects.Location;
import com.example.cargotracker.handlingms.domain.model.valueobjects.TrackingNumber;
import com.example.cargotracker.handlingms.domain.model.valueobjects.VoyageNumber;
import java.time.LocalDateTime;

/**
 * 荷役作業登録イベント（US15）。
 *
 * <p>{@code HandlingActivity} Aggregate が {@code RegisterHandlingActivityCommand} を
 * 受け付けた際に発行する。trackingms（IT6 以降）は本イベントを購読して追跡履歴に
 * 反映する。</p>
 *
 * <p>関連: ADR-0012 handlingms と trackingms の責務分離</p>
 */
public record HandlingActivityRegisteredEvent(
        String activityId,
        TrackingNumber trackingNumber,
        HandlingType handlingType,
        Location location,
        LocalDateTime occurredAt,
        VoyageNumber voyageNumber,
        HandlerId operatorId,
        ClaimVerification claimVerification,
        CargoSnapshot cargoSnapshot,
        boolean unexpected) {
}
