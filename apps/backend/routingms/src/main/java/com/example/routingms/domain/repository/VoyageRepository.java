package com.example.routingms.domain.repository;

import com.example.routingms.domain.model.aggregates.Voyage;
import com.example.routingms.domain.model.valueobjects.RouteSearchSpecification;
import com.example.routingms.domain.model.valueobjects.VoyageSearchCriteria;
import com.example.routingms.domain.model.valueobjects.VoyageNumber;
import java.time.Instant;
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
     *   <li><strong>すでに出てしまった航海</strong>（押さえられない船を前提にした経路を出さない）</li>
     * </ul>
     *
     * @param notDepartedBefore この時刻より前に出発した航海は対象にしない。航海スケジュールの
     *     一覧が既定で「本日以降」に絞っているのと同じ扱いにする。ここを開けると、古い便ほど
     *     日数計算上は早く着くため<strong>上位を占める</strong>
     */
    List<Voyage> findCandidates(RouteSearchSpecification specification, Instant notDepartedBefore);
}
