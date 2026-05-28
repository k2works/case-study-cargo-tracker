package com.example.trackingms.interfaces.events;

import com.example.trackingms.domain.events.TrackingInitializedEvent;
import com.example.trackingms.domain.model.TransportStatus;
import com.example.trackingms.infrastructure.repositories.mybatis.TrackingSummaryMapper;
import org.axonframework.eventhandling.EventHandler;
import org.springframework.stereotype.Component;

/**
 * 追跡 Read Model 更新用の EventHandler（US14 / IT5 1.4）。
 *
 * <p>{@link TrackingInitializedEvent} を受信して {@code tracking_summary} に 1 行 INSERT する。
 * 初期状態は {@code TransportStatus.NOT_RECEIVED}（受入条件）、{@code misrouted=false}。
 * 後続イベント（{@code TransportStatusUpdatedEvent} 等、IT5 タスク 2.x）で
 * {@code current_status} を更新する。</p>
 *
 * <p>本ハンドラはローカルイベント（trackingms 内発行）を購読するため、default プロセッサ
 * （event store source）で動作する。cross-service 受信は別ハンドラ
 * （{@code TrackingIssuanceRequestedEventHandler}）に分離されている。</p>
 */
@Component
public class TrackingSummaryProjectionEventHandler {

    private final TrackingSummaryMapper trackingSummaryMapper;

    public TrackingSummaryProjectionEventHandler(TrackingSummaryMapper trackingSummaryMapper) {
        this.trackingSummaryMapper = trackingSummaryMapper;
    }

    @EventHandler
    public void on(TrackingInitializedEvent event) {
        trackingSummaryMapper.insertTrackingSummary(
                event.trackingNumber(),
                event.bookingId(),
                TransportStatus.NOT_RECEIVED.name()
        );
    }
}
