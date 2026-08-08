package com.example.cargotracker.routing.domain.repository;

import com.example.cargotracker.routing.domain.model.Voyage;
import com.example.cargotracker.routing.domain.model.VoyageNumber;
import com.example.cargotracker.shared.domain.model.Location;
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

    /**
     * 出発地に寄港し、かつ目的地にも寄港する航海を返す（US08 の探索対象）。
     *
     * <p><strong>全航海を読み込まない。</strong> 港と便が増えるほど、
     * 経路割り当て画面を開くだけで全件が載る。<strong>順序の判定
     * （目的地に着いた後に出発地を出る航海を除く）はドメインが行う</strong>ため、
     * ここでの絞り込みは「両方の港に寄るか」までである。
     */
    java.util.List<Voyage> findConnecting(Location origin, Location destination);

    /**
     * 航海ごとの割当済み重量を返す（US09 の空き容量判定）。
     *
     * <p><strong>航海ごとに引き直さない</strong>（N+1）。確定済みの貨物だけを数える。
     */
    java.util.Map<VoyageNumber, com.example.cargotracker.routing.domain.model.RoutingWeight>
            findAssignedWeights(java.util.List<VoyageNumber> voyageNumbers);

    /**
     * 特定の予約を除いて、航海ごとの割当済み重量を返す（US13 の確定時の再判定）。
     *
     * <p><strong>確定しようとしている貨物自身は数えから除く。</strong> 除かないと、
     * すでに割り当て済みの貨物を確定するときに自分の重量を二重に数え、
     * 空きがあるのに「満船」と判定する。
     *
     * @param excludeBookingId 数えから除く予約 ID。{@code null} なら除かない
     */
    java.util.Map<VoyageNumber, com.example.cargotracker.routing.domain.model.RoutingWeight>
            findAssignedWeights(
                    java.util.List<VoyageNumber> voyageNumbers,
                    java.util.UUID excludeBookingId);
}
