package com.example.cargotracker.booking.application.internal.queryservices;

import java.util.List;
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
     */
    List<BookingView> search(String origin, String destination, String status);

    Optional<BookingView> findById(String bookingId);
}
