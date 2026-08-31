package com.example.trackingms.application.internal.outboundservices.acl;

import com.example.trackingms.application.internal.queryservices.ShipperCargoSnapshot;
import com.example.trackingms.domain.model.valueobjects.TrackingNumber;
import java.util.List;
import java.util.Optional;

/** 荷主境界の判定に要る bookingms Snapshot を引く出力ポート。 */
public interface ShipperCargoSnapshotFinder {

    /** 追跡番号に対応する予約 Snapshot。見つからなければ空。 */
    Optional<ShipperCargoSnapshot> findByTrackingNumber(TrackingNumber trackingNumber);

    /**
     * その荷主の貨物 Snapshot をすべて返す（一覧の入口）。
     *
     * <p><strong>先に荷主で絞る。</strong>追跡側の直近 N 件から絞ると、貨物が増えた荷主の
     * 古い貨物が窓の外に落ちて<strong>一覧から消える</strong>——件数だけで壊れる形である。
     * 1 件ずつ {@link #findByTrackingNumber} で確かめる形も、貨物の数だけ問い合わせが増える。
     */
    List<ShipperCargoSnapshot> findByShipperId(long shipperId);
}
