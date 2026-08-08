package com.example.cargotracker.tracking.handling.application.internal.outboundservices.acl;

import com.example.cargotracker.tracking.handling.domain.model.CargoSnapshot;
import java.util.Optional;

/**
 * 予約の写しを取得する出力ポート（Handling → Booking の ACL）。
 *
 * <p>正典は {@code domain-model.md}「BC 間 ACL ポート一覧」である。同表はポート名を
 * {@code CargoSnapshot} としていたが、それは<strong>運ぶ値の名前</strong>であり、
 * 同名の値オブジェクトと衝突して実装できない。ポートは複数形で名づける
 * （IT5 の {@code CargoRouteAssignments} と同じ形）。
 *
 * <p><strong>実装は Booking 側の {@code infrastructure/acl} が持つ。</strong>
 * ここに置くのは interface だけであり、荷役は相手のドメインを知らない。
 */
public interface CargoSnapshots {

    /**
     * 追跡番号から予約の写しを取得する。
     *
     * <p>画面が受け取るのは追跡番号であり、予約 ID ではない。
     * <strong>引き当ては Booking 側の仕事である</strong>（追跡番号を持つのは貨物）。
     *
     * @param trackingNumber 追跡番号
     * @return 見つからなければ空
     */
    Optional<CargoSnapshot> findByTrackingNumber(String trackingNumber);
}
