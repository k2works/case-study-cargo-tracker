package com.example.cargotracker.handling.application.internal.outboundservices;

import java.util.UUID;

/**
 * 予約存在確認ポート。
 * handling コンテキストが booking コンテキストの存在確認を行うための ACL インターフェース。
 */
public interface BookingExistencePort {

    /**
     * 指定した予約 ID が存在することを確認する。
     * 存在しない場合は {@link com.example.cargotracker.handling.application.internal.commandservices.BookingNotFoundException} を投げる。
     *
     * @param bookingId 確認する予約 ID
     */
    void verifyExists(UUID bookingId);
}
