package com.example.cargotracker.estimation.domain.model.port;

import com.example.cargotracker.estimation.domain.model.CargoType;
import com.example.cargotracker.estimation.domain.model.RouteCandidate;
import com.example.cargotracker.shared.domain.model.Location;

import java.time.LocalDate;
import java.util.List;

/**
 * 経路候補を提供するポートインターフェース。
 * Estimation コンテキストのドメイン層が依存するアウトバウンドポート。
 * IT3 スタブから IT4 実データ連携への移行を可能にするために抽出。
 */
public interface RouteCandidateProvider {
    /**
     * 指定した条件で経路候補を検索する。
     *
     * @param origin          出発地
     * @param destination     目的地
     * @param arrivalDeadline 到着期限
     * @param cargoType       貨物種別
     * @return 経路候補のリスト（空リストは候補なしを意味する）
     */
    List<RouteCandidate> findCandidates(
            Location origin,
            Location destination,
            LocalDate arrivalDeadline,
            CargoType cargoType
    );
}
