package com.example.cargotracker.routing.application.internal.outboundservices;

import java.util.Optional;
import java.util.UUID;

/**
 * routing コンテキストが booking コンテキストにアクセスするためのポートインターフェース。
 *
 * <p>routing → booking の直接依存を防ぐアンチコラプションレイヤー。
 * 実装は booking コンテキスト側のアダプタが担う。
 */
public interface BookingQueryPort {

    /**
     * 予約 ID で予約情報のスナップショットを取得する。
     *
     * @param bookingId 予約 ID（UUID）
     * @return 予約情報。存在しない場合は空の Optional
     */
    Optional<BookingSnapshot> findById(UUID bookingId);
}
