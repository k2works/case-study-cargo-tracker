package com.example.cargotracker.booking.application.internal.queryservices;

import com.example.cargotracker.shared.application.paging.Page;
import com.example.cargotracker.shared.application.paging.PageRequest;
import java.util.Optional;

/**
 * 貨物予約の読み取り（CQRS のクエリ側）。
 *
 * <p>実装はインフラ層に置く（ArchUnit ルール 3）。
 */
public interface BookingQueryService {

    /**
     * 一覧を取得する。
     *
     * @param origin      出発地 UN/LOCODE。未指定なら絞り込まない
     * @param destination 目的地 UN/LOCODE。未指定なら絞り込まない
     * @param status      予約状態。未指定なら絞り込まない
     * @param page        ページ送りの要求
     */
    Page<BookingView> search(String origin, String destination, String status, PageRequest page);

    /**
     * 経路割り当て待ちの予約（US06 / US08。経路設計者の作業入口）。
     *
     * <p>対象は引き渡し済み（{@code ROUTE_PROPOSED}）で経路が未割り当てのもの。
     * <strong>既定の並び順は希望期限の昇順</strong>である（`ui_design.md`）。
     * 経路設計者が朝に見るのは「どれが一番切羽詰まっているか」であり、
     * **予約 ID 順では役に立たない**。
     */
    Page<BookingView> findAwaitingRouting(PageRequest page);

    Optional<BookingView> findById(String bookingId);
}
