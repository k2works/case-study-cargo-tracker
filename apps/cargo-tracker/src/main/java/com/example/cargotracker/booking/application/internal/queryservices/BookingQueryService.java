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

    Optional<BookingView> findById(String bookingId);
}
