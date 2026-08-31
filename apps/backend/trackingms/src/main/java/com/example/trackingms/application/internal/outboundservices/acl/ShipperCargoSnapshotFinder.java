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

    /**
     * この中でシミュレーション由来のもの（[ADR-030] 決定 3・IT15）。
     *
     * <p><strong>まとめて問う。</strong>1 件ずつ確かめると、例外が増えた日に
     * 問い合わせがその数だけ増える——US37 の継続実行は例外を意図的に起こす。
     *
     * <p><strong>越境点はこのポート 1 つに保つ。</strong>由来を問うためだけに別の
     * 出口を作ると、bookingms への入口が 2 つになり、認可も契約も二重に管理することになる。
     *
     * @return 由来がシミュレーションである追跡番号。渡した中に無いものは含まない
     */
    java.util.Set<String> simulatedAmong(List<TrackingNumber> trackingNumbers);
}
