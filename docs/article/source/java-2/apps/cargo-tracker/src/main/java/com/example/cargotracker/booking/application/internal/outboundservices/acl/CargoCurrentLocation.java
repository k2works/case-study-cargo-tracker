package com.example.cargotracker.booking.application.internal.outboundservices.acl;

import com.example.cargotracker.shared.domain.model.valueobjects.Location;
import java.util.Optional;

/**
 * 貨物のいまの場所を読む出力ポート（Booking → Tracking の ACL。US30）。
 *
 * <p><strong>輸送中の貨物をどこで降ろせるかは、いまどこにいるかで決まる。</strong>
 * 現在地は Tracking が持つ（最後の荷役の発生場所）。
 * <strong>Booking に写し取らない</strong> — 写すと荷役が進むたびに 2 か所がずれる。
 *
 * <p><strong>返すのは素の値だけである</strong>（ADR-005）。共有カーネルの
 * {@link Location} は BC をまたいで使ってよい。
 */
public interface CargoCurrentLocation {

    /**
     * 貨物のいまの場所。
     *
     * <p><strong>見つからないことを例外にしない。</strong> 追跡の記録がまだ無い
     * 貨物はある。空を返すと、陸揚げ地の候補は旅程の残りだけになる。
     */
    Optional<Location> findByTrackingNumber(String trackingNumber);
}
