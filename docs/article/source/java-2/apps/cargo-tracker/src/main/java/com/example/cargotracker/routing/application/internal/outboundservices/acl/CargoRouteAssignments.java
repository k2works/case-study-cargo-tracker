package com.example.cargotracker.routing.application.internal.outboundservices.acl;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * 確定した経路を貨物予約に反映する ACL ポート（US09 / US11）。
 *
 * <p><strong>境界では、どちらの BC の型も使わない。</strong> Routing の値オブジェクトを
 * ここに置くと、実装する側（Booking）が Routing のドメインを直接参照することになり、
 * ACL を置いた動機そのものが消える（IT4 で ArchUnit ルール 4 に捕まった形）。
 *
 * <p>ポートは利用する側（Routing）が定義し、アダプタは提供する側（Booking）が実装する。
 */
public interface CargoRouteAssignments {

    /**
     * 経路を割り当てる。
     *
     * <p>旅程は<strong>丸ごと置き換える</strong>。予約状態は変えない
     * （経路を確定しても {@code BookingStatus} は動かない）。
     *
     * @param bookingId 予約 ID
     * @param legs      区間。<strong>順序に意味がある</strong>
     * @return 割り当てた結果
     */
    AssignmentResult assign(UUID bookingId, List<LegAssignment> legs);

    /**
     * 割り当てる区間 1 本。
     *
     * @param voyageNumber           航海番号
     * @param loadLocationUnlocode   積込港の UN/LOCODE
     * @param unloadLocationUnlocode 荷降港の UN/LOCODE
     * @param loadTime               積込予定日時
     * @param unloadTime             荷降予定日時
     */
    record LegAssignment(
            String voyageNumber,
            String loadLocationUnlocode,
            String unloadLocationUnlocode,
            Instant loadTime,
            Instant unloadTime) {
    }

    /** 割り当ての結果。 */
    enum AssignmentResult {
        /** 割り当てた。 */
        ASSIGNED,
        /** 予約が見つからない。 */
        NOT_FOUND,
        /** 経路を割り当てられる状態ではない、または旅程が予約と一致しない。 */
        REJECTED,
        /** 別の担当者が先に更新していた（楽観的ロック）。 */
        CONFLICTED
    }
}
