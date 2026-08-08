package com.example.cargotracker.tracking.domain.model;

import com.example.cargotracker.shared.domain.model.Location;
import java.time.Instant;

/**
 * 追跡イベント。時系列で記録される追跡の出来事。
 *
 * <p>航海番号は積込・荷降しでのみ意味を持つ。受領・引取・通関では {@code null} である。
 *
 * @param type         イベント種別
 * @param occurredAt   発生日時
 * @param location     発生場所
 * @param voyageNumber 関連する航海番号。無い場合は {@code null}
 */
public record TrackingActivityEvent(
        TrackingEventType type,
        Instant occurredAt,
        Location location,
        TrackingVoyageNumber voyageNumber) {

    public TrackingActivityEvent {
        if (type == null) {
            throw new IllegalArgumentException("イベント種別は必須です");
        }
        if (occurredAt == null) {
            throw new IllegalArgumentException("発生日時は必須です");
        }
        if (location == null) {
            throw new IllegalArgumentException("発生場所は必須です");
        }
    }
}
