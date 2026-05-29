package com.example.trackingms.interfaces.events;

import com.example.trackingms.domain.events.CargoMisroutedEvent;
import com.example.trackingms.domain.events.TrackingInitializedEvent;
import com.example.trackingms.domain.events.TransportStatusUpdatedEvent;
import com.example.trackingms.domain.model.TransportStatus;
import com.example.trackingms.infrastructure.repositories.mybatis.TrackingEventMapper;
import com.example.trackingms.infrastructure.repositories.mybatis.TrackingSummaryMapper;
import org.axonframework.eventhandling.EventHandler;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/**
 * 追跡 Read Model 更新用の EventHandler（US14 / US17 / IT5 1.4 + 2.3）。
 *
 * <p>{@code TrackingActivity} 集約が発行するローカルイベントを購読して
 * {@code tracking_summary} と {@code tracking_event}（履歴）を更新する。
 * 履歴は時系列追記のみで更新しない。</p>
 *
 * <p>本ハンドラはローカルイベント（trackingms 内発行）を購読するため、default プロセッサ
 * （event store source）で動作する。cross-service 受信は別ハンドラ
 * （{@code TrackingIssuanceRequestedEventHandler}）に分離されている。</p>
 */
@Component
public class TrackingSummaryProjectionEventHandler {

    private final TrackingSummaryMapper summaryMapper;
    private final TrackingEventMapper eventMapper;

    public TrackingSummaryProjectionEventHandler(TrackingSummaryMapper summaryMapper,
                                                 TrackingEventMapper eventMapper) {
        this.summaryMapper = summaryMapper;
        this.eventMapper = eventMapper;
    }

    @EventHandler
    public void on(TrackingInitializedEvent event) {
        summaryMapper.insertTrackingSummary(
                event.trackingNumber(),
                event.bookingId(),
                TransportStatus.NOT_RECEIVED.name()
        );
        // 履歴にも初期化イベントを記録する（source=SYSTEM）。
        eventMapper.insertTrackingEvent(
                event.trackingNumber(),
                LocalDateTime.now(),
                "TRACKING_INITIALIZED",
                TransportStatus.NOT_RECEIVED.name(),
                null, null, null,
                "SYSTEM",
                null
        );
    }

    @EventHandler
    public void on(TransportStatusUpdatedEvent event) {
        summaryMapper.updateStatus(
                event.trackingNumber(),
                event.toStatus().name(),
                event.unlocode(),
                event.voyageNumber(),
                event.occurredAt()
        );
        eventMapper.insertTrackingEvent(
                event.trackingNumber(),
                event.occurredAt(),
                "STATUS_UPDATED",
                event.toStatus().name(),
                event.unlocode(),
                event.voyageNumber(),
                null,
                "MANUAL",
                event.description()
        );
    }

    @EventHandler
    public void on(CargoMisroutedEvent event) {
        summaryMapper.markMisrouted(event.trackingNumber(), event.occurredAt());
    }
}
