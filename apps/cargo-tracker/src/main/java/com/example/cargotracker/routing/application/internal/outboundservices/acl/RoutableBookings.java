package com.example.cargotracker.routing.application.internal.outboundservices.acl;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

/**
 * 経路を割り当てる対象の予約を読む ACL ポート。
 *
 * <p>経路探索には予約の内容（出発地・目的地・希望期限・貨物種別・重量）が要る。
 *
 * <p><strong>境界では、どちらの BC の型も使わない。</strong> Routing の値オブジェクトを
 * ここに置くと、実装する側（Booking）が Routing のドメインを直接参照することになり、
 * BC の分離（ADR-005・ArchUnit ルール 4）が壊れる。<strong>ACL を置いた動機そのものが
 * 消える。</strong> 受け渡すのは素の値であり、Routing のことばへの翻訳は Routing 側で行う。
 *
 * <p>ポートは利用する側（Routing）が定義し、アダプタは提供する側（Booking）が
 * 実装する。IT2 の {@code ShipperExistenceChecker} と同じ形である。
 */
public interface RoutableBookings {

    /**
     * 経路割り当ての対象になる予約を読む。
     *
     * @param bookingId 予約 ID
     * @return 存在しない、または経路割り当ての対象でなければ空
     */
    Optional<RoutableBooking> find(UUID bookingId);

    /**
     * 経路探索に必要な予約の内容。
     *
     * @param bookingId           予約 ID
     * @param originUnlocode      出発地の UN/LOCODE
     * @param destinationUnlocode 目的地の UN/LOCODE
     * @param arrivalDeadline     希望到着期限
     * @param cargoType           貨物種別（{@code GENERAL} / {@code HAZARDOUS}
     *                            / {@code REFRIGERATED}）
     * @param weightKilograms     重量（キログラム）
     * @param shipperName         荷主名（画面の見出しに出す）
     */
    record RoutableBooking(
            UUID bookingId,
            String originUnlocode,
            String destinationUnlocode,
            LocalDate arrivalDeadline,
            String cargoType,
            BigDecimal weightKilograms,
            String shipperName) {
    }
}
