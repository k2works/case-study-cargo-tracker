package com.example.cargotracker.trackingms.domain.model.events;

import com.example.cargotracker.trackingms.domain.model.valueobjects.CargoItinerary;
import com.example.cargotracker.trackingms.domain.model.valueobjects.Location;
import com.example.cargotracker.trackingms.domain.model.valueobjects.TrackingNumber;
import com.example.cargotracker.trackingms.domain.model.valueobjects.TransportStatus;
import java.time.LocalDateTime;

/**
 * 追跡集約が初期化されたことを表すドメインイベント（US18）。
 *
 * <p>{@code CargoTrackedEvent} を契機に内部発行される {@code InitializeTrackingCommand} の
 * 結果として発行される。Read Model {@code tracking_summary} の初期レコードを生成する。</p>
 */
public record TrackingInitializedEvent(
        TrackingNumber trackingNumber,
        String bookingId,
        CargoItinerary itinerary,
        Location origin,
        Location destination,
        LocalDateTime estimatedArrival,
        TransportStatus initialStatus) { }
