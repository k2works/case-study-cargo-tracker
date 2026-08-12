package com.example.cargotracker.routing.domain.model;
import com.example.cargotracker.routing.domain.model.aggregates.Voyage;
import com.example.cargotracker.routing.domain.model.valueobjects.VoyageNumber;
import com.example.cargotracker.routing.domain.model.valueobjects.CarrierMovement;
import com.example.cargotracker.routing.domain.model.entities.ProposedRoute;
import com.example.cargotracker.routing.domain.model.valueobjects.RoutingCargoType;
import com.example.cargotracker.routing.domain.model.valueobjects.RoutingCriteria;
import com.example.cargotracker.routing.domain.model.valueobjects.RoutingWeight;

import com.example.cargotracker.shared.domain.model.valueobjects.Location;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * 経路候補を探す（US08）。
 *
 * <p>探すのは<strong>1 つの航海の中で、出発地から目的地までを乗り通せる区間</strong>で
 * ある。途中の港から乗ることも、途中の港で降りることもできる。
 * 複数の航海を乗り継ぐ経路は本システムでは扱わない
 * （{@code proposed_route} が航海番号を 1 つだけ持つことに対応する）。
 *
 * <p><strong>打ち切りの条件を持つ。</strong> 経由回数の上限を超える候補は作らない。
 * 上限が無いと、港と航海が増えるほど候補が増え、経路設計者は選べなくなる。
 */
public class RouteSearchService {

    private final FreightEstimator freightEstimator;

    /**
     * 業務のタイムゾーン。<strong>期限は利用者の暦の上の日付である。</strong>
     * サーバの標準時で判断すると、時差の分だけ「期限当日」を取りこぼす。
     */
    private final ZoneId businessZone;

    public RouteSearchService(FreightEstimator freightEstimator, ZoneId businessZone) {
        this.freightEstimator = freightEstimator;
        this.businessZone = businessZone;
    }

    /**
     * 条件に合う候補を推奨順で返す。
     *
     * <p>推奨順は ①期限を満たす候補が先 ②直行が先 ③所要日数の短い順
     * ④概算費用の安い順である。<strong>概算である費用は最後の基準に留める</strong>
     * （ADR-008）。精度の低い数字を順位の主軸にしない。
     *
     * @param criteria 探索条件
     * @param voyages  探索の対象になる航海
     * @return 推奨順の候補（表示順を振り済み）
     */
    public List<ProposedRoute> search(RoutingCriteria criteria, List<Voyage> voyages) {
        return search(criteria, voyages, java.util.Map.of());
    }

    /**
     * 空き容量を見て候補を返す（US09）。
     *
     * @param assignedWeights 航海番号ごとの割当済み重量。<strong>渡さないと
     *     「何件割り当てても空きあり」になる</strong>
     */
    public List<ProposedRoute> search(
            RoutingCriteria criteria,
            List<Voyage> voyages,
            java.util.Map<VoyageNumber, RoutingWeight> assignedWeights) {
        // **条件の無い探索は「全部が候補」になる。** 黙って空を返すと、
        // 候補ゼロと区別がつかない
        if (criteria == null) {
            throw new IllegalArgumentException("探索条件は必須です");
        }
        if (voyages == null) {
            throw new IllegalArgumentException("探索対象の航海は必須です（0 件は空のリストで表す）");
        }
        List<ProposedRoute> found = new ArrayList<>();
        for (Voyage voyage : voyages) {
            toRoute(criteria, voyage, assignedWeights.get(voyage.voyageNumber()))
                    .ifPresent(found::add);
        }
        found.sort(recommendationOrder());

        List<ProposedRoute> ordered = new ArrayList<>(found.size());
        for (int i = 0; i < found.size(); i++) {
            // 表示順は 1 から振る。**画面が並べ替えの規則を持たないようにする**
            ordered.add(found.get(i).withPriority(i + 1));
        }
        return List.copyOf(ordered);
    }

    private Comparator<ProposedRoute> recommendationOrder() {
        return Comparator
                .comparing((ProposedRoute r) -> !r.deadlineSatisfied())
                .thenComparing(r -> !r.isDirect())
                .thenComparingInt(ProposedRoute::transitDays)
                .thenComparing(r -> r.estimatedCost().value())
                .thenComparing(r -> r.voyageNumber().value());
    }

    private Optional<ProposedRoute> toRoute(
            RoutingCriteria criteria, Voyage voyage, RoutingWeight assignedWeight) {
        List<CarrierMovement> movements = voyage.schedule().carrierMovements();

        // **同じ港を 2 度通る航海がある。** 最初に見つけた出発だけを見ると、
        // 経由の少ない乗り方があるのに提示できず、上限で刈られて候補ごと消える
        int boarding = -1;
        int landing = -1;
        for (int i = 0; i < movements.size(); i++) {
            if (movements.get(i).departureLocation().equals(criteria.origin())) {
                // **乗った後に降りる。** 目的地に着いてから出発地へ寄る航海を使わない
                int candidate = indexOfArrivalFrom(movements, criteria.destination(), i);
                // 経由が少ない乗り方を選ぶ。**乗る港が遅いほど経由は減る**
                if (candidate >= 0 && (boarding < 0 || candidate - i < landing - boarding)) {
                    boarding = i;
                    landing = candidate;
                }
            }
        }
        if (boarding < 0) {
            return Optional.empty();
        }

        List<Location> transitPorts = new ArrayList<>();
        for (int i = boarding; i < landing; i++) {
            transitPorts.add(movements.get(i).arrivalLocation());
        }
        // 打ち切り。上限を超える候補は作らない
        if (transitPorts.size() > criteria.maxTransitCount()) {
            return Optional.empty();
        }

        Instant departure = movements.get(boarding).departureTime();
        Instant arrival = movements.get(landing).arrivalTime();
        int transitDays = transitDays(departure, arrival);

        return Optional.of(ProposedRoute.of(
                voyage.voyageNumber(),
                // **探索が選んだ位置をそのまま持たせる。** 確定するときに時刻から
                // 逆算し直すと、同じ港を 2 度通る航海でどの周回かが曖昧になる
                new ProposedRoute.Path(transitPorts,
                        new ProposedRoute.LegRange(boarding, landing)),
                new ProposedRoute.Timing(departure, arrival, transitDays),
                freightEstimator.estimate(criteria.weight(), transitDays, criteria.cargoType()),
                new ProposedRoute.Handling(
                        criteria.cargoType(),
                        voyage.accepts(RoutingCargoType.HAZARDOUS),
                        voyage.accepts(RoutingCargoType.REFRIGERATED),
                        voyage.hasCapacityFor(criteria.weight(), assignedWeight)),
                satisfiesDeadline(arrival, criteria.arrivalDeadline())));
    }

    /**
     * 所要日数。
     *
     * <p><strong>切り上げる。</strong> 12 日 20 時間の航海を 12 日と呼ぶと、
     * 到着を 1 日早く見せることになる。経路設計は日数で比べるため、
     * 端数を落とすと「早い便」を誤って選ぶ。
     */
    private int transitDays(Instant departure, Instant arrival) {
        Duration duration = Duration.between(departure, arrival);
        long days = duration.toDays();
        return (int) (duration.minusDays(days).isZero() ? days : days + 1);
    }

    /**
     * 期限を満たすか。
     *
     * <p><strong>日付単位で比べる</strong>（{@code domain-model.md} ビジネスルール 2-1）。
     * 期限は時刻を持たない日付、到着は時刻を持つ。素朴に比べると期限が 0 時として
     * 扱われ、<strong>期限当日に着く便がすべて期限超過になる</strong>。
     */
    private boolean satisfiesDeadline(Instant arrival, LocalDate deadline) {
        LocalDate arrivalDate = arrival.atZone(businessZone).toLocalDate();
        return !arrivalDate.isAfter(deadline);
    }

    private int indexOfArrivalFrom(List<CarrierMovement> movements, Location location, int from) {
        for (int i = from; i < movements.size(); i++) {
            if (movements.get(i).arrivalLocation().equals(location)) {
                return i;
            }
        }
        return -1;
    }
}
