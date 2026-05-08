package com.example.trackingms.domain.ports;

import com.example.trackingms.domain.model.aggregates.TrackingActivity;
import com.example.trackingms.domain.model.valueobjects.TrackingBookingId;
import com.example.trackingms.domain.model.valueobjects.TrackingNumber;

import java.util.Optional;

/**
 * 追跡アクティビティ リポジトリポート
 */
public interface TrackingActivityRepository {
    TrackingActivity save(TrackingActivity activity);
    Optional<TrackingActivity> findByTrackingNumber(TrackingNumber trackingNumber);
    Optional<TrackingActivity> findByBookingId(TrackingBookingId bookingId);
    void update(TrackingActivity activity);
}
