package com.example.cargotracker.handlingms.domain.model.events;

import com.example.cargotracker.handlingms.domain.model.valueobjects.HandlingType;
import com.example.cargotracker.handlingms.domain.model.valueobjects.Location;
import com.example.cargotracker.handlingms.domain.model.valueobjects.TrackingNumber;

/**
 * 予定外荷役検知イベント（US15 受入条件 7）。
 *
 * <p>{@code HandlingActivity} Aggregate が {@code CargoSnapshot.isExpectedHandling} で
 * {@code false} を検出した場合に、{@link HandlingActivityRegisteredEvent} と併せて発行する。</p>
 *
 * <p>記録自体は許容するが、管理者向けの警告として残す。将来 trackingms が
 * 本イベントを購読して例外（{@code TrackingException}）に昇格させる予定。</p>
 */
public record UnexpectedHandlingDetectedEvent(
        String activityId,
        TrackingNumber trackingNumber,
        HandlingType handlingType,
        Location actualLocation,
        Location expectedOrigin,
        Location expectedDestination) {
}
