package com.example.cargotracker.handling.domain.model.aggregates;

import com.example.cargotracker.handling.domain.model.events.DomainEvent;
import com.example.cargotracker.handling.domain.model.events.HandlingEventRecordedEvent;
import com.example.cargotracker.handling.domain.model.valueobjects.HandlingEventType;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

/**
 * 荷役イベント集約ルート。
 * 港湾・輸送における荷役作業（積み込み・荷降ろし・通関・積み替え・引取・手動更新）を記録する。
 */
public class HandlingEvent {

    private final HandlingEventId id;
    private final UUID bookingId;
    private final HandlingEventType eventType;
    private final String locationCode;
    private final LocalDateTime completionTime;
    private final String memo;
    private final List<DomainEvent> domainEvents = new ArrayList<>();

    private HandlingEvent(HandlingEventId id, UUID bookingId, HandlingEventType eventType,
                          String locationCode, LocalDateTime completionTime, String memo) {
        this.id = id;
        this.bookingId = bookingId;
        this.eventType = eventType;
        this.locationCode = locationCode;
        this.completionTime = completionTime;
        this.memo = memo;
    }

    /**
     * 荷役イベントを記録する（新規発生）。ドメインイベントを発行する。
     */
    public static HandlingEvent record(HandlingEventId id, UUID bookingId, HandlingEventType eventType,
                                       String locationCode, LocalDateTime completionTime, String memo) {
        if (id == null) throw new IllegalArgumentException("荷役イベント ID は null にできません");
        if (bookingId == null) throw new IllegalArgumentException("予約 ID は null にできません");
        if (eventType == null) throw new IllegalArgumentException("荷役イベント種別は null にできません");
        if (locationCode == null || locationCode.isBlank()) throw new IllegalArgumentException("場所コードは null または空にできません");
        if (completionTime == null) throw new IllegalArgumentException("完了日時は null にできません");

        HandlingEvent event = new HandlingEvent(id, bookingId, eventType, locationCode, completionTime, memo);
        event.domainEvents.add(new HandlingEventRecordedEvent(id, bookingId, eventType));
        return event;
    }

    /**
     * ストレージから荷役イベントを再構成する。ドメインイベントは発行しない。
     */
    public static HandlingEvent reconstitute(HandlingEventId id, UUID bookingId, HandlingEventType eventType,
                                              String locationCode, LocalDateTime completionTime, String memo) {
        if (id == null) throw new IllegalArgumentException("荷役イベント ID は null にできません");
        if (bookingId == null) throw new IllegalArgumentException("予約 ID は null にできません");
        if (eventType == null) throw new IllegalArgumentException("荷役イベント種別は null にできません");
        if (locationCode == null || locationCode.isBlank()) throw new IllegalArgumentException("場所コードは null または空にできません");
        if (completionTime == null) throw new IllegalArgumentException("完了日時は null にできません");

        return new HandlingEvent(id, bookingId, eventType, locationCode, completionTime, memo);
    }

    public HandlingEventId getId() {
        return id;
    }

    public UUID getBookingId() {
        return bookingId;
    }

    public HandlingEventType getEventType() {
        return eventType;
    }

    public String getLocationCode() {
        return locationCode;
    }

    public LocalDateTime getCompletionTime() {
        return completionTime;
    }

    public String getMemo() {
        return memo;
    }

    public List<DomainEvent> getDomainEvents() {
        return Collections.unmodifiableList(domainEvents);
    }
}
