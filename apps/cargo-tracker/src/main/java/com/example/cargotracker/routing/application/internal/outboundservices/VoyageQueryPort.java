package com.example.cargotracker.routing.application.internal.outboundservices;

import com.example.cargotracker.routing.domain.model.Voyage;

import java.util.List;
import java.util.Optional;

/**
 * routing コンテキストが航海データにアクセスするためのポートインターフェース。
 *
 * <p>queryservices から infrastructure 層への直接依存を防ぐ。
 * 実装は {@code routing/infrastructure/repositories/VoyageRepositoryImpl} が担う。
 */
public interface VoyageQueryPort {

    /**
     * 出発地・目的地を含む航海を検索する。
     *
     * @param originLocode      出発地 UN/LOCODE
     * @param destinationLocode 目的地 UN/LOCODE
     * @return 該当する航海リスト
     */
    List<Voyage> searchVoyages(String originLocode, String destinationLocode);

    /**
     * 航海番号で航海を取得する。
     *
     * @param voyageNumber 航海番号
     * @return 航海（存在しない場合は空の Optional）
     */
    Optional<Voyage> findByVoyageNumber(String voyageNumber);

    /**
     * 全航海を取得する。
     *
     * @return 登録済みの全航海リスト
     */
    List<Voyage> findAll();
}
