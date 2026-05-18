package com.example.cargotracker.trackingms.application.eventhandlers;

import com.example.cargotracker.trackingms.domain.model.events.TrackingInitializedEvent;
import com.example.cargotracker.trackingms.domain.model.events.TransportStatusUpdatedEvent;
import com.example.cargotracker.trackingms.domain.model.valueobjects.TransportStatus;
import com.example.cargotracker.trackingms.infrastructure.persistence.TrackingEventMapper;
import com.example.cargotracker.trackingms.infrastructure.persistence.TrackingEventRecord;
import com.example.cargotracker.trackingms.infrastructure.persistence.TrackingSummaryMapper;
import com.example.cargotracker.trackingms.infrastructure.persistence.TrackingSummaryRecord;
import org.axonframework.messaging.eventhandling.annotation.EventHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * trackingms の Projection EventHandler（US18）。
 *
 * <p>{@code TrackingInitializedEvent} / {@code TransportStatusUpdatedEvent} を購読して
 * {@code tracking_summary} と {@code tracking_event} の Read Model を更新する。</p>
 *
 * <p>{@code CargoTrackedEvent} / {@code HandlingActivityRegisteredEvent}（クロスサービス Event）
 * の購読は TI06 で実装予定。本クラスは {@code tracking-projections} processor で動作する。</p>
 */
@Component
public class TrackingProjectionsEventHandler {

    private static final Logger LOG = LoggerFactory.getLogger(TrackingProjectionsEventHandler.class);

    private final TrackingSummaryMapper summaryMapper;
    private final TrackingEventMapper eventMapper;

    public TrackingProjectionsEventHandler(
            TrackingSummaryMapper summaryMapper,
            TrackingEventMapper eventMapper) {
        this.summaryMapper = summaryMapper;
        this.eventMapper = eventMapper;
    }

    @EventHandler
    public void on(TrackingInitializedEvent event) {
        LOG.debug("TrackingInitializedEvent received: trackingNumber={}", event.trackingNumber().value());

        var summary = new TrackingSummaryRecord(
                event.trackingNumber().value(),
                event.bookingId(),
                event.initialStatus().name(),
                event.origin().unLocode().value(),
                null,
                event.origin().unLocode().value(),
                event.destination().unLocode().value(),
                event.estimatedArrival(),
                null,
                false,
                null,
                null, null, 0L);
        summaryMapper.insert(summary);

        eventMapper.insert(new TrackingEventRecord(
                null,
                event.trackingNumber().value(),
                event.estimatedArrival(),
                null,
                "TRACKING_INITIALIZED",
                event.initialStatus().name(),
                event.origin().unLocode().value(),
                null,
                null,
                "追跡が初期化されました",
                "SYSTEM"));
    }

    @EventHandler
    public void on(TransportStatusUpdatedEvent event) {
        LOG.debug("TransportStatusUpdatedEvent received: trackingNumber={}, newStatus={}, source={}",
                event.trackingNumber().value(), event.newStatus(), event.source());

        boolean misrouted = (event.newStatus() == TransportStatus.MISROUTED);
        summaryMapper.updateCurrentStatus(
                event.trackingNumber().value(),
                event.newStatus().name(),
                event.location().unLocode().value(),
                null,
                misrouted,
                event.updatedAt());

        if (event.newStatus() == TransportStatus.DELIVERED) {
            summaryMapper.updateDeliveredAt(event.trackingNumber().value(), event.updatedAt());
        }

        eventMapper.insert(new TrackingEventRecord(
                null,
                event.trackingNumber().value(),
                event.updatedAt(),
                null,
                "STATUS_UPDATE",
                event.newStatus().name(),
                event.location().unLocode().value(),
                null,
                null,
                "状態が " + event.newStatus().name() + " に更新されました",
                event.source().name()));
    }
}
