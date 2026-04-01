package com.example.cargotracker.booking.application.internal.outboundservices;

import java.util.Optional;
import java.util.UUID;
import java.util.List;

/**
 * 荷主存在確認ポート。
 * booking コンテキストが shipper コンテキストの存在確認を行うための ACL インターフェース。
 */
public interface ShipperExistencePort {

    record ShipperOption(UUID id, String name, String email) {}

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

    /**
     * 予約登録フォームで選択可能な荷主一覧を返す。
     *
     * @return 荷主選択肢一覧
     */
    List<ShipperOption> findAll();
}
