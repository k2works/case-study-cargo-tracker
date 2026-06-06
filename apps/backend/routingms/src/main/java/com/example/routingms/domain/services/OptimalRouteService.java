package com.example.routingms.domain.services;

import com.example.routingms.domain.model.RouteCandidate;
import com.example.routingms.domain.model.RouteLeg;
import com.example.routingms.domain.model.RouteSearchSpecification;
import com.example.routingms.domain.projections.VoyageProjection;

import java.math.BigDecimal;
import java.time.temporal.ChronoUnit;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 経路候補算出ドメインサービス（US08 / Routing Context）。
 *
 * <p>航海スケジュール（Voyage Read Model）と探索制約（{@link RouteSearchSpecification}）を入力に、
 * 寄港地の接続可能性を評価して経路候補を推奨順に算出する。Routing の集約は {@code Voyage} のみであり
 * （domain-model.md）、経路候補は永続集約を持たない算出結果として返す。</p>
 *
 * <p>IT8 T1.7 で BFS による多段経由探索（最大 {@link #MAX_LEGS} 段）に移行。直行 + 1 経由のみだった
 * 旧実装（O(n²) の二重ループ）から、グラフ上の BFS 探索でグローバルな最適経路を発見可能にした。
 * 循環防止のため同一港の二重通過を抑止する。完全な Dijkstra/A*（重み付きキュー + ヒューリスティック）
 * は将来 voyage 数 / 港数の増加時に切替（現状の最大経由 3 段では BFS で実用十分）。</p>
 *
 * <p>推奨順は「直行便を最優先 → 所要日数の短い順 → 概算費用の安い順」で並べる。
 * 期限内に到達可能な経路がない場合は空リストを返す（候補なし）。</p>
 *
 * <p><b>概算費用について</b>：MVP では正式な運賃モデル（Billing コンテキストの FareCalculator）を持たないため、
 * {@code estimatedCost = 区間数 × LEG_BASE_FEE + 所要日数 × DAILY_RATE}（JPY 固定）の簡易ヒューリスティックで
 * 算出する。現状は所要日数に連動するため推奨順の第 2・第 3 キー（日数・費用）はおおむね同順となる。正式な運賃が
 * 導入された段階で費用が独立した推奨キーになる。</p>
 */
public class OptimalRouteService {

    /** 探索する最大経由段数（leg 数）。3 段 = 直行 + 1 経由 + 2 経由まで。 */
    private static final int MAX_LEGS = 3;
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

        List<RouteCandidate> candidates = bfsSearch(spec, eligible);

        return candidates.stream()
                .filter(candidate -> withinDeadline(candidate, spec))
                .sorted(recommendationOrder())
                .toList();
    }

    /**
     * BFS で出発地から目的地までの経路を全列挙する（最大 {@link #MAX_LEGS} 段）。
     * 同一港を 2 回通る経路は循環として除外。
     */
    private List<RouteCandidate> bfsSearch(RouteSearchSpecification spec, List<VoyageProjection> voyages) {
        List<RouteCandidate> results = new ArrayList<>();
        Deque<SearchState> queue = new ArrayDeque<>();

        // 初期状態: spec.origin から出発する全 voyage を queue に投入
        for (VoyageProjection v : voyages) {
            if (matches(v.getOriginUnlocode(), spec.origin())) {
                Set<String> visited = new HashSet<>();
                visited.add(spec.origin());
                visited.add(v.getDestUnlocode());
                queue.add(new SearchState(List.of(toLeg(v)), visited, v));
            }
        }

        while (!queue.isEmpty()) {
            SearchState state = queue.poll();
            VoyageProjection last = state.lastVoyage;

            if (matches(last.getDestUnlocode(), spec.destination())) {
                // 目的地に到達 → 経路候補として登録
                results.add(toCandidate(state.legs));
            } else if (state.legs.size() < MAX_LEGS) {
                // 探索深さ上限未満なら接続便を探索（上限以上なら打ち切り）
                for (VoyageProjection next : voyages) {
                    if (matches(next.getOriginUnlocode(), last.getDestUnlocode())
                            && !state.visitedPorts.contains(next.getDestUnlocode())
                            && !next.getDepartureDate().isBefore(last.getArrivalDate())) {
                        List<RouteLeg> newLegs = new ArrayList<>(state.legs);
                        newLegs.add(toLeg(next));
                        Set<String> newVisited = new HashSet<>(state.visitedPorts);
                        newVisited.add(next.getDestUnlocode());
                        queue.add(new SearchState(List.copyOf(newLegs), newVisited, next));
                    }
                }
            }
        }

        return results;
    }

    /**
     * BFS 探索の中間状態。
     *
     * @param legs           ここまでの経路（leg 列）
     * @param visitedPorts   既に通過した港（循環抑止用）
     * @param lastVoyage     最後の leg に対応する Voyage（接続判定で利用）
     */
    private record SearchState(List<RouteLeg> legs,
                               Set<String> visitedPorts,
                               VoyageProjection lastVoyage) {
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
