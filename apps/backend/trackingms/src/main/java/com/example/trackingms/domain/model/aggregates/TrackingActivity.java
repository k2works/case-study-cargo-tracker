package com.example.trackingms.domain.model.aggregates;

import com.example.trackingms.domain.model.valueobjects.TrackingBookingId;
import com.example.trackingms.domain.model.valueobjects.TrackingEventType;
import com.example.trackingms.domain.model.valueobjects.TrackingNumber;
import com.example.trackingms.domain.model.valueobjects.TrackingStatus;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * 追跡アクティビティ（集約ルート）
 */
public class TrackingActivity {
    private Long id;
    private final TrackingNumber trackingNumber;
    private final TrackingBookingId bookingId;
    private TrackingStatus transportStatus;
    private final List<TrackingActivityEvent> events = new ArrayList<>();

    /** 新規作成コンストラクタ（NOT_RECEIVED で初期化） */
    public TrackingActivity(TrackingNumber trackingNumber, TrackingBookingId bookingId) {
        Objects.requireNonNull(trackingNumber, "trackingNumber must not be null");
        Objects.requireNonNull(bookingId, "bookingId must not be null");
        this.trackingNumber = trackingNumber;
        this.bookingId = bookingId;
        this.transportStatus = TrackingStatus.NOT_RECEIVED;
    }

    /** 永続化済み集約再構成コンストラクタ */
    public TrackingActivity(Long id, TrackingNumber trackingNumber, TrackingBookingId bookingId,
                             TrackingStatus transportStatus, List<TrackingActivityEvent> events) {
        this(trackingNumber, bookingId);
        this.id = id;
        this.transportStatus = transportStatus;
        if (events != null) {
            this.events.addAll(events);
        }
    }

    /**
     * 追跡イベントを追加し、貨物状態を更新する
     */
    public void addEvent(TrackingActivityEvent event) {
        Objects.requireNonNull(event, "event must not be null");
        events.add(event);
        this.transportStatus = deriveStatus(event.getEventType());
    }

    /**
     * 貨物状態を手動更新する（追跡管理者用）
     */
    public void updateStatus(TrackingStatus newStatus) {
        Objects.requireNonNull(newStatus, "newStatus must not be null");
        this.transportStatus = newStatus;
    }

    /**
     * イベント種別から追跡ステータスを導出する
     */
    private TrackingStatus deriveStatus(TrackingEventType eventType) {
        return switch (eventType) {
            case RECEIVE -> TrackingStatus.RECEIVED;
            case LOAD -> TrackingStatus.LOADED;
            case UNLOAD -> TrackingStatus.UNLOADED;
            case CLAIM -> TrackingStatus.CLAIMED;
            case CUSTOMS -> TrackingStatus.UNKNOWN;
        };
    }

    public Long getId() { return id; }
    public TrackingNumber getTrackingNumber() { return trackingNumber; }
    public TrackingBookingId getBookingId() { return bookingId; }
    public TrackingStatus getTransportStatus() { return transportStatus; }
    public List<TrackingActivityEvent> getEvents() { return Collections.unmodifiableList(events); }
}
