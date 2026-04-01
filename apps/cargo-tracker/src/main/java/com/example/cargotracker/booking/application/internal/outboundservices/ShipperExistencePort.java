package com.example.cargotracker.booking.application.internal.outboundservices;

import java.util.Optional;
import java.util.UUID;

/**
 * 荷主存在確認ポート。
 * booking コンテキストが shipper コンテキストの存在確認を行うための ACL インターフェース。
 */
public interface ShipperExistencePort {

    /**
     * 指定した荷主 ID が存在することを確認する。
     * 存在しない場合は {@link com.example.cargotracker.booking.application.internal.commandservices.ShipperNotFoundException} を投げる。
     *
     * @param shipperId 確認する荷主 ID
     */
    void verifyExists(UUID shipperId);

    /**
     * 指定した荷主 ID の名前を返す。
     *
     * @param shipperId 荷主 ID
     * @return 荷主名（存在しない場合は empty）
     */
    Optional<String> findNameById(UUID shipperId);
}
