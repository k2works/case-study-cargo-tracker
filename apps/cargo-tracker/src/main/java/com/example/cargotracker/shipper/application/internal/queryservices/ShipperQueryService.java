package com.example.cargotracker.shipper.application.internal.queryservices;

import java.util.List;
import java.util.Optional;

/**
 * 荷主の読み取り（CQRS のクエリ側）。
 *
 * <p>実装はインフラ層に置く。**アプリケーション層がインフラ層を直接参照しない**
 * ルール（ArchUnit ルール 3）を守るため、ここでは境界だけを定義する。
 */
public interface ShipperQueryService {

    /**
     * 一覧を取得する。
     *
     * @param keyword 荷主名・荷主コード・メールアドレスの部分一致。未指定なら全件
     */
    List<ShipperView> search(String keyword);

    Optional<ShipperView> findById(String shipperId);
}
