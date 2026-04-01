package com.example.cargotracker.booking.application;

import java.util.UUID;

/**
 * 荷主存在確認ポート。
 * booking コンテキストが shipper コンテキストの存在確認を行うための ACL インターフェース。
 */
public interface ShipperExistencePort {

    /**
     * 指定した荷主 ID が存在することを確認する。
     * 存在しない場合は {@link ShipperNotFoundException} を投げる。
     *
     * @param shipperId 確認する荷主 ID
     * @throws ShipperNotFoundException 荷主が存在しない場合
     */
    void verifyExists(UUID shipperId);
}
