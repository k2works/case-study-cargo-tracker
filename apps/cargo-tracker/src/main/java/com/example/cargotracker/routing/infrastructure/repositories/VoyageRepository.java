package com.example.cargotracker.routing.infrastructure.repositories;

import com.example.cargotracker.routing.application.internal.outboundservices.VoyageQueryPort;
import com.example.cargotracker.routing.domain.model.Voyage;

import java.util.List;
import java.util.Optional;

/**
 * 航海（Voyage）リポジトリポート。
 *
 * <p>航海スケジュール検索に使用するクエリポート。
 */
public interface VoyageRepository extends VoyageQueryPort {

    /**
     * 出発地・目的地を含む航海を検索する。
     *
     * <p>直行便（出発地→目的地）と経由便（出発地→中継港→目的地）の両方を返す。
     *
     * @param originLocode      出発地 UN/LOCODE
     * @param destinationLocode 目的地 UN/LOCODE
     * @return 該当する航海リスト（空の場合は空リスト）
     */
    List<Voyage> searchVoyages(String originLocode, String destinationLocode);

    /**
     * 航海番号で航海を取得する。
     *
     * @param voyageNumber 航海番号
     * @return 航海（存在しない場合は空の Optional）
     */
    Optional<Voyage> findByVoyageNumber(String voyageNumber);
}
