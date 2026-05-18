package com.example.cargotracker.handlingms.domain.model.events;

import com.example.cargotracker.handlingms.domain.model.valueobjects.HandlerId;
import com.example.cargotracker.handlingms.domain.model.valueobjects.Location;
import com.example.cargotracker.handlingms.domain.model.valueobjects.TrackingNumber;
import java.time.LocalDateTime;

/**
 * 貨物状態手動更新イベント（US17）。
 *
 * <p>追跡管理者の手動更新によって発行される。IT6 の trackingms 新設時に
 * 本イベントを購読して {@code tracking_event} に追記する。</p>
 *
 * <p>関連 ADR: ADR-0012 handlingms と trackingms の責務分離</p>
 */
public record CargoStatusUpdatedEvent(
        String activityId,
        TrackingNumber trackingNumber,
        String newStatus,
        Location location,
        LocalDateTime updatedAt,
        HandlerId operatorId) {
}
