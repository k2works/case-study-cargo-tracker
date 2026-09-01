package com.example.bookingms.infrastructure.repositories;

import com.example.bookingms.domain.model.aggregates.Shipper;

/**
 * 実利用者の一覧から、シミュレーション由来を外す条件（[ADR-030] 決定 3）。
 *
 * <p><strong>写しを増やさない。</strong>同じ条件を一覧・件数・別の一覧へ書き写すと、
 * 帯を変えたときに書き直した側だけが古いまま残る。帯そのものは
 * {@link Shipper#SIMULATED_CODE_PREFIX} が持ち、ここはそれを SQL の言葉にするだけである。
 *
 * <p><strong>名指しの照会では使わない。</strong>予約番号や追跡番号を指定した参照まで
 * 外すと、シミュレーション自身が業務を進められなくなる。
 */
public final class SimulatedShipperFilter {

    /** 荷主を {@code s} で結合した問い合わせに置く条件。 */
    public static final String EXCLUDE_SIMULATED =
            "s.shipper_code NOT LIKE '" + Shipper.SIMULATED_CODE_PREFIX + "%'";

    private SimulatedShipperFilter() {
    }
}
