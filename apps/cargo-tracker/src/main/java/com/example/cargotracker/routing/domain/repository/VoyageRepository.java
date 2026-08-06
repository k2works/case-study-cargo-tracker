package com.example.cargotracker.routing.domain.repository;

import com.example.cargotracker.routing.domain.model.Voyage;
import com.example.cargotracker.routing.domain.model.VoyageNumber;
import java.util.Optional;

/** 航海の出力ポート。実装はインフラ層に置く（DIP）。 */
public interface VoyageRepository {

    /**
     * 新規登録する。
     *
     * <p><strong>集約全体（航海と運送区間）を 1 つの操作として保存する。</strong>
     * 区間だけが残る・航海だけが残るという中途半端な状態を作らない。
     */
    void save(Voyage voyage);

    Optional<Voyage> findByVoyageNumber(VoyageNumber voyageNumber);

    boolean existsByVoyageNumber(VoyageNumber voyageNumber);
}
