package com.example.cargotracker.booking.application.internal.outboundservices.acl;

import java.math.BigDecimal;
import java.util.List;

/**
 * 確定しようとしている経路に、いま空きがあるかを確かめる出力ポート
 *（Booking → Routing の ACL）。
 *
 * <p><strong>候補の算出時に判定した値は使えない。</strong> 算出から確定までの間に
 * 他の貨物が同じ便に割り当てられていれば、算出時に「空きあり」だった便が
 * 満船になっている（IT5 レビュー M3）。
 *
 * <p>境界では素の値だけを渡す。Booking は {@code Voyage} も {@code RoutingWeight} も
 * 知らない（ADR-005・ArchUnit ルール 4）。
 */
public interface VoyageCapacityPort {

    /**
     * 指定した便すべてに、その重量を積む空きがあるか。
     *
     * <p><strong>この貨物がすでに割り当てられている分は差し引いて数える。</strong>
     * そうしないと、割り当て済みの貨物を確定するときに自分の重量を二重に数え、
     * 空きがあるのに「満船」と判定する。
     *
     * @param voyageNumbers    確かめる航海番号
     * @param weightKilograms  積む重量
     * @param excludeBookingId 数えから除く予約 ID（この貨物自身）
     * @return 空きが無い航海番号（すべて空きがあれば空）
     */
    List<String> findFullVoyages(
            List<String> voyageNumbers, BigDecimal weightKilograms, String excludeBookingId);
}
