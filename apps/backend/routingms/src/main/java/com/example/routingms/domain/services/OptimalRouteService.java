package com.example.routingms.domain.services;

import com.example.routingms.domain.model.RouteCandidate;
import com.example.routingms.domain.model.RouteLeg;
import com.example.routingms.domain.model.RouteSearchSpecification;
import com.example.routingms.domain.projections.VoyageProjection;

import java.math.BigDecimal;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * 経路候補算出ドメインサービス（US08 / Routing Context）。
 *
 * <p>航海スケジュール（Voyage Read Model）と探索制約（{@link RouteSearchSpecification}）を入力に、
 * 寄港地の接続可能性を評価して経路候補を推奨順に算出する。Routing の集約は {@code Voyage} のみであり
 * （domain-model.md）、経路候補は永続集約を持たない算出結果として返す。</p>
 *
 * <p>MVP では直行便および 1 経由（乗り継ぎ 1 回）までの単純探索を行う。多段経由の探索は
 * 段階拡張とし IT8 バッファで対応する（iteration_plan-4.md リスク対策）。</p>
 *
 * <p>推奨順は「直行便を最優先 → 所要日数の短い順 → 概算費用の安い順」で並べる。
 * 期限内に到達可能な経路がない場合は空リストを返す（候補なし）。</p>
 */
public class OptimalRouteService {

    private static final String GENERAL_CARGO_TYPE = "GENERAL";
    private static final BigDecimal LEG_BASE_FEE = new BigDecimal("200000");
    private static final BigDecimal DAILY_RATE = new BigDecimal("40000");
    private static final String DEFAULT_CURRENCY = "JPY";

    /**
     * 経路候補を算出する。
     *
     * @param spec    探索制約（出発地・目的地・到着期限・貨物種別）
     * @param voyages 候補算出の対象となる航海スケジュール一覧（Read Model）
     * @return 推奨順に並べた経路候補。該当なしの場合は空リスト
     */
    public List<RouteCandidate> calculate(RouteSearchSpecification spec, List<VoyageProjection> voyages) {
        List<VoyageProjection> eligible = voyages.stream()
                .filter(voyage -> accepts(voyage, spec.cargoType()))
                .toList();

        List<RouteCandidate> candidates = new ArrayList<>();
        candidates.addAll(directCandidates(spec, eligible));
        candidates.addAll(transshipmentCandidates(spec, eligible));

        return candidates.stream()
                .filter(candidate -> withinDeadline(candidate, spec))
                .sorted(recommendationOrder())
                .toList();
    }

    private List<RouteCandidate> directCandidates(RouteSearchSpecification spec, List<VoyageProjection> voyages) {
        return voyages.stream()
                .filter(voyage -> matches(voyage.getOriginUnlocode(), spec.origin())
                        && matches(voyage.getDestUnlocode(), spec.destination()))
                .map(voyage -> toCandidate(List.of(toLeg(voyage))))
                .toList();
    }

    private List<RouteCandidate> transshipmentCandidates(
            RouteSearchSpecification spec, List<VoyageProjection> voyages) {
        List<RouteCandidate> result = new ArrayList<>();
        for (VoyageProjection first : voyages) {
            if (!matches(first.getOriginUnlocode(), spec.origin())) {
                continue;
            }
            if (matches(first.getDestUnlocode(), spec.destination())) {
                continue; // 直行便は directCandidates で扱う
            }
            for (VoyageProjection second : voyages) {
                if (!matches(second.getOriginUnlocode(), first.getDestUnlocode())) {
                    continue;
                }
                if (!matches(second.getDestUnlocode(), spec.destination())) {
                    continue;
                }
                if (second.getDepartureDate().isBefore(first.getArrivalDate())) {
                    continue; // 乗り継ぎ便が接続便の到着前に出発 → 接続不可
                }
                result.add(toCandidate(List.of(toLeg(first), toLeg(second))));
            }
        }
        return result;
    }

    /**
     * 航海が指定貨物種別を受け入れるか。対応貨物種別が未登録の航海は一般貨物のみ受け入れる
     * （Voyage 集約の不変条件、domain-model.md）。
     */
    private boolean accepts(VoyageProjection voyage, String cargoType) {
        List<String> acceptedTypes = voyage.getAcceptedCargoTypes();
        if (acceptedTypes == null || acceptedTypes.isEmpty()) {
            return GENERAL_CARGO_TYPE.equals(cargoType);
        }
        return acceptedTypes.contains(cargoType);
    }

    private boolean withinDeadline(RouteCandidate candidate, RouteSearchSpecification spec) {
        return !candidate.arrivalTime().toLocalDate().isAfter(spec.arrivalDeadline());
    }

    private RouteLeg toLeg(VoyageProjection voyage) {
        return new RouteLeg(
                voyage.getVoyageNumber(),
                voyage.getOriginUnlocode(),
                voyage.getDestUnlocode(),
                voyage.getDepartureDate(),
                voyage.getArrivalDate());
    }

    private RouteCandidate toCandidate(List<RouteLeg> legs) {
        int estimatedDays = (int) ChronoUnit.DAYS.between(
                legs.get(0).loadTime().toLocalDate(),
                legs.get(legs.size() - 1).unloadTime().toLocalDate());
        BigDecimal estimatedCost = LEG_BASE_FEE.multiply(BigDecimal.valueOf(legs.size()))
                .add(DAILY_RATE.multiply(BigDecimal.valueOf(estimatedDays)));
        return new RouteCandidate(legs, estimatedDays, estimatedCost, DEFAULT_CURRENCY);
    }

    private Comparator<RouteCandidate> recommendationOrder() {
        return Comparator.comparingInt((RouteCandidate candidate) -> candidate.legs().size())
                .thenComparingInt(RouteCandidate::estimatedDays)
                .thenComparing(RouteCandidate::estimatedCost);
    }

    private boolean matches(String actual, String expected) {
        return actual != null && actual.equals(expected);
    }
}
