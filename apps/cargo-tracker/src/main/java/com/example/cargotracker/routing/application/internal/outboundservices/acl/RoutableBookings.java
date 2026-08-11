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
     * @param route  経路の端点と期限
     * @param cargo  貨物の仕様
     * @param shipperName         荷主名（画面の見出しに出す）
     * @param misroutedFrom       <strong>誤配のときの貨物の現在地</strong>（US28）。
     *                            誤配でなければ {@code null}。ここから経路を引き直す
     */
    record RoutableBooking(
            UUID bookingId,
            Route route,
            CargoSpec cargo,
            String shipperName,
            String misroutedFrom) {

        /**
         * 経路の端点と期限。
         *
         */
        public record Route(
                String originUnlocode, String destinationUnlocode, LocalDate arrivalDeadline) { }

        /**
         * 貨物の仕様。
         *
         * @param type            貨物種別
         */
        public record CargoSpec(String type, BigDecimal weightKilograms) { }

        // --- 呼び出し側が使う名前（委譲するアクセサ）---

        /** @return 出発地 */
        public String originUnlocode() {
            return route.originUnlocode();
        }

        /** @return 目的地 */
        public String destinationUnlocode() {
            return route.destinationUnlocode();
        }

        /** @return 希望到着期限 */
        public LocalDate arrivalDeadline() {
            return route.arrivalDeadline();
        }

        /** @return 貨物種別 */
        public String cargoType() {
            return cargo.type();
        }

        /** @return 重量（キログラム） */
        public BigDecimal weightKilograms() {
            return cargo.weightKilograms();
        }


        /** 誤配のため引き直すのか。**画面の出し分けは同じ述語を使う。** */
        public boolean isMisrouted() {
            return misroutedFrom != null && !misroutedFrom.isBlank();
        }
    }
}
