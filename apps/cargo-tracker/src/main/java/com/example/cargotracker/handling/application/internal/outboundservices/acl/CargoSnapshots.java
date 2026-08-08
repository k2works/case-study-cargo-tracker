package com.example.cargotracker.handling.application.internal.outboundservices.acl;

import java.util.List;
import java.util.Optional;

/**
 * 予約の写しを取得する出力ポート（Handling → Booking の ACL）。
 *
 * <p>正典は {@code domain-model.md}「BC 間 ACL ポート一覧」である。同表はポート名を
 * {@code CargoSnapshot} としていたが、それは<strong>運ぶ値の名前</strong>であり、
 * 同名の値オブジェクトと衝突して実装できない。ポートは複数形で名づける
 * （IT5 の {@code CargoRouteAssignments} と同じ形）。
 *
 * <p><strong>境界を越える値は本インターフェースの内側に置く。</strong> 荷役の
 * {@code domain.model} に置くと、実装する Booking 側が相手の
 * {@code domain.model} を参照することになり、ArchUnit ルール 4 に落ちる
 * （ACL ポートのパッケージだけが越境点として除外されている）。
 *
 * <p>実装は Booking 側の {@code infrastructure/acl} が持つ。
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
    Optional<Snapshot> findByTrackingNumber(String trackingNumber);

    /**
     * 予約の写し。<strong>すべて素の値である。</strong>
     *
     * @param bookingId     予約 ID
     * @param origin        予約の出発地（UN/LOCODE）
     * @param destination   予約の目的地（UN/LOCODE）
     * @param consigneeName 予約に登録された荷受人氏名。未登録なら {@code null}（US16）
     * @param legs          予定ルートの区間。経路が未割り当てなら空
     */
    record Snapshot(
            String bookingId, String origin, String destination,
            String consigneeName, List<Leg> legs) {

        public Snapshot {
            legs = List.copyOf(legs == null ? List.of() : legs);
        }
    }

    /**
     * 予定ルートの区間 1 つ分。
     *
     * @param voyageNumber   航海番号
     * @param loadLocation   積込港（UN/LOCODE）
     * @param unloadLocation 荷降港（UN/LOCODE）
     */
    record Leg(String voyageNumber, String loadLocation, String unloadLocation) {
    }
}
