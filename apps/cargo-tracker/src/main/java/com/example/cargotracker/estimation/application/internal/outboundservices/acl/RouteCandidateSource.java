package com.example.cargotracker.estimation.application.internal.outboundservices.acl;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * ルート候補の取得（ADR-023）。
 *
 * <p><strong>ポートは Estimation が所有する</strong>（ADR-012）。実装（アダプタ）は
 * 提供側の {@code routing/infrastructure/acl} に置く。
 *
 * <p><strong>BC 間で型を渡さない。</strong> ここに現れるのは素の値だけであり、
 * Routing の {@code ProposedRoute} や {@code RoutingCargoType} は現れない
 * （ADR-005 / ADR-012）。
 *
 * <p><strong>失敗がどこに出るか</strong>（ADR-021）: これは<strong>問い合わせ</strong>で
 * あり状態を変えない。候補が 0 件だったことは {@link Candidates} が理由つきで返し、
 * <strong>見積詳細の画面に出る</strong>。
 */
public interface RouteCandidateSource {

    /**
     * 条件に合う候補を推奨順で返す。
     *
     * @param query 探索条件
     */
    Candidates findCandidates(Query query);

    /**
     * 探索条件。
     *
     * @param originUnlocode      出発地
     * @param destinationUnlocode 目的地
     * @param arrivalDeadline     希望到着期限
     * @param cargoType           貨物種別（列挙子名）
     * @param weightKg            重量（kg）
     */
    record Query(
            String originUnlocode,
            String destinationUnlocode,
            LocalDate arrivalDeadline,
            String cargoType,
            BigDecimal weightKg) { }

    /**
     * 探索の結果。
     *
     * <p><strong>0 件の理由を区別して返す</strong>（ADR-023）。「便が無い」と
     * 「期限に間に合わない」では、営業担当者が次に取る行動が違う。
     *
     * @param candidates    候補（推奨順）。0 件のこともある
     * @param anyVoyage     その区間を走る便が 1 本でもあったか
     */
    record Candidates(List<Candidate> candidates, boolean anyVoyage) {

        public Candidates {
            candidates = candidates == null ? List.of() : List.copyOf(candidates);
        }

        /** 候補があるか。 */
        public boolean isEmpty() {
            return candidates.isEmpty();
        }

        /**
         * その区間を走る便がそもそも無いか。
         *
         * <p><strong>期限の問題と混ぜない。</strong> 便が無いなら、別の港を提案するか
         * 他社を当たることになる。
         */
        public boolean noVoyage() {
            return !anyVoyage;
        }

        /**
         * 便はあるが期限に間に合わないか。
         *
         * <p>荷主に期限の延長を相談する、という次の行動につながる。
         */
        public boolean deadlineTooTight() {
            return anyVoyage && candidates.isEmpty();
        }
    }

    /**
     * 候補 1 件（素の値）。
     *
     * @param voyageNumber  航海番号
     * @param transitPort   経由港。直行なら {@code null}
     * @param transitDays   所要日数
     * @param estimatedCost 概算費用
     * @param currency      通貨
     */
    record Candidate(
            String voyageNumber,
            String transitPort,
            int transitDays,
            BigDecimal estimatedCost,
            String currency) { }
}
