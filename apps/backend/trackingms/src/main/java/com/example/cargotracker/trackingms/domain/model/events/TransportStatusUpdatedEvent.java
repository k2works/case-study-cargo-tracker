package com.example.cargotracker.trackingms.domain.model.events;

import com.example.cargotracker.trackingms.domain.model.valueobjects.EventSource;
import com.example.cargotracker.trackingms.domain.model.valueobjects.HandlerId;
import com.example.cargotracker.trackingms.domain.model.valueobjects.Location;
import com.example.cargotracker.trackingms.domain.model.valueobjects.TrackingNumber;
import com.example.cargotracker.trackingms.domain.model.valueobjects.TransportStatus;
import java.time.LocalDateTime;
import org.axonframework.eventsourcing.annotation.EventTag;

/**
 * 貨物の輸送状態が更新されたことを表すドメインイベント（US17 移管後・TI06）。
 *
 * <p>発生源は {@link EventSource} で識別される。荷役由来は {@code HANDLING}、
 * 管理者手動更新は {@code MANUAL}、システム自動は {@code SYSTEM}。</p>
 */
public record TransportStatusUpdatedEvent(
        @EventTag TrackingNumber trackingNumber,
        TransportStatus newStatus,
        Location location,
        LocalDateTime updatedAt,
        HandlerId operatorId,
        EventSource source) { }
