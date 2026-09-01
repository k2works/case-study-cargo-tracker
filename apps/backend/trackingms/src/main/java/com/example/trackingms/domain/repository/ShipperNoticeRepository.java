package com.example.trackingms.domain.repository;

import com.example.trackingms.domain.model.valueobjects.ShipperNotice;
import com.example.trackingms.domain.model.valueobjects.TrackingNumber;
import java.util.List;

/** 荷主へ届ける知らせの読み出し（US39）。 */
public interface ShipperNoticeRepository {

    /**
     * その貨物たちへの知らせのうち、<strong>読んだ位置より新しいもの</strong>を古い順に返す。
     *
     * <p><strong>古い順である。</strong>お知らせの一覧（新しい順）とは逆——ポップアップは
     * 起きた順に出さないと、出港より先に到着が出る。
     *
     * <p><strong>まとめて引く。</strong>貨物 1 件ずつ問い合わせると、貨物が増えた荷主ほど
     * 問い合わせが増える。
     */
    List<ShipperNotice> findNewerThan(List<TrackingNumber> trackingNumbers, long lastNoticeId,
            int limit);
}
