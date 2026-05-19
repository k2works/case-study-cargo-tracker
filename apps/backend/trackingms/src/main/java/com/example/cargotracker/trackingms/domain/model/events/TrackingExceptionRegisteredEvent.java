package com.example.cargotracker.trackingms.domain.model.events;

import com.example.cargotracker.trackingms.domain.model.valueobjects.TrackingNumber;
import java.time.LocalDateTime;

/**
 * 追跡例外が登録されたドメインイベント（US19）。
 *
 * <p>このイベントの受信時、{@link com.example.cargotracker.trackingms.domain.model.aggregates.TrackingActivity}
 * 集約は {@code currentStatus} を {@link com.example.cargotracker.trackingms.domain.model.valueobjects.TransportStatus#EXCEPTION}
 * に遷移させ、Projection は {@code tracking_exception} テーブルに INSERT すると同時に
 * {@code tracking_summary.current_status} を {@code EXCEPTION} へ直接更新する。
 * このパスでは {@code TransportStatusUpdatedEvent} は発行されないため、
 * EXCEPTION への遷移は {@code tracking_event} テーブルには STATUS_UPDATE として記録されない。</p>
 */
public record TrackingExceptionRegisteredEvent(
        TrackingNumber trackingNumber,
        String exceptionId,
        String exceptionType,
        LocalDateTime occurredAt,
        String occurredUnlocode,
        String description,
        String operatorId) { }
