package com.example.cargotracker.trackingms.domain.model.events;

import com.example.cargotracker.trackingms.domain.model.valueobjects.TrackingNumber;
import java.time.LocalDateTime;

/**
 * 追跡例外が登録されたドメインイベント（US19）。
 *
 * <p>発行後、Projection が {@code tracking_exception} テーブルに INSERT し、
 * 続いて {@code TransportStatusUpdatedEvent(EXCEPTION)} を発行して状態を更新する。</p>
 */
public record TrackingExceptionRegisteredEvent(
        TrackingNumber trackingNumber,
        String exceptionId,
        String exceptionType,
        LocalDateTime occurredAt,
        String occurredUnlocode,
        String description,
        String operatorId) { }
