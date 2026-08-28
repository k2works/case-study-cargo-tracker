package com.example.trackingms.application.port;

import com.example.trackingms.application.internal.queryservices.ShipperCargoSnapshot;
import com.example.trackingms.domain.model.TrackingNumber;
import java.util.Optional;

/** 荷主境界の判定に要る bookingms Snapshot を引く出力ポート。 */
public interface ShipperCargoSnapshotFinder {

    /** 追跡番号に対応する予約 Snapshot。見つからなければ空。 */
    Optional<ShipperCargoSnapshot> findByTrackingNumber(TrackingNumber trackingNumber);
}
