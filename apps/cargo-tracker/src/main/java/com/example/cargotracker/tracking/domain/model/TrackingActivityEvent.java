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
        TrackingVoyageNumber voyageNumber,
        TrackingEventSource source,
        String recordedBy) {

    /** 荷役由来のイベント（US15 / US16）。**記録者は荷役側が持つ。** */
    public static TrackingActivityEvent fromHandling(
            TrackingEventType type, Instant occurredAt, Location location,
            TrackingVoyageNumber voyageNumber) {
        return new TrackingActivityEvent(
                type, occurredAt, location, voyageNumber, TrackingEventSource.HANDLING, null);
    }

    /**
     * 手で入れたイベント（US17）。
     *
     * <p><strong>記録者を必ず持つ。</strong> 手動更新は業務の判断であり、
     * 誰が入れたかが分からないと後から確かめようがない。
     */
    public static TrackingActivityEvent manual(
            TrackingEventType type, Instant occurredAt, Location location,
            TrackingVoyageNumber voyageNumber, String recordedBy) {
        if (recordedBy == null || recordedBy.isBlank()) {
            throw new IllegalArgumentException("手動更新の記録者は必須です");
        }
        return new TrackingActivityEvent(
                type, occurredAt, location, voyageNumber, TrackingEventSource.MANUAL, recordedBy);
    }

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
        if (source == null) {
            source = TrackingEventSource.HANDLING;
        }
    }

    /** 手で入れたイベントか。**画面で「手動」と示すために使う。** */
    public boolean manual() {
        return source == TrackingEventSource.MANUAL;
    }
}
