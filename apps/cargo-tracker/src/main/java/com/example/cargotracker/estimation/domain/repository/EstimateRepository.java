package com.example.cargotracker.estimation.domain.repository;

import com.example.cargotracker.estimation.domain.model.Estimate;
import com.example.cargotracker.estimation.domain.model.EstimateId;
import java.util.Optional;

/**
 * 見積の出力ポート（DIP）。
 *
 * <p>実装は {@code infrastructure/repositories} に置く。
 * <strong>集約はインフラを参照しない。</strong>
 */
public interface EstimateRepository {

    /** 見積を保存する（候補も一緒に保存する）。 */
    void save(Estimate estimate);

    /** 見積番号で引く。<strong>見つからないことは例外ではない。</strong> */
    Optional<Estimate> findByEstimateId(EstimateId estimateId);
}
