package com.example.routingms.application.port;

import com.example.routingms.domain.model.RouteSearchSpecification;
import com.example.routingms.domain.model.Voyage;
import com.example.routingms.domain.model.VoyageNumber;
import java.util.List;
import java.util.Optional;

/**
 * 航海の出力ポート。実装はインフラ層に置き、依存の向きを内向きに保つ。
 */
public interface VoyageRepository {

    Voyage save(Voyage voyage);

    Optional<Voyage> findByVoyageNumber(VoyageNumber voyageNumber);

    /** 条件に合う航海を探す。件数の上限は呼び出し側（ユースケース）が決める。 */
    List<Voyage> search(VoyageSearchCriteria criteria, int limit);

    /** 条件に合う航海の総数。上限で切った件数と区別して伝えるために要る。 */
    int countMatching(VoyageSearchCriteria criteria);

    /**
     * 経路探索の対象になりうる航海を引く（US08）。
     *
     * <p>絞りは<strong>広めに引く</strong>。ここで絞りすぎると候補が落ち、経路設計者には
     * 「その経路は無い」としか見えない。運べるか・順序が合うかの判定は集約が行う
     * （同じ判定を SQL に書き直すと、SQL と集約で答えが食い違う。IT3 でそれが起きた）。
     *
     * <p>落とせるのは 2 つだけである。
     *
     * <ul>
     *   <li>その貨物種別を運べない航海（航海の属性で決まる）</li>
     *   <li>期限より後にしか出ない航海（最初の出発が期限を過ぎていれば、どの区間も使えない）</li>
     * </ul>
     */
    List<Voyage> findCandidates(RouteSearchSpecification specification);
}
