package com.example.routingms.domain.model;

import com.example.shared.domain.model.Location;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 経路候補算出（US08）。航海スケジュールの上を探索して、運べる経路をすべて挙げる。
 *
 * <p>制約の判断は<strong>すべてここと値オブジェクトにある</strong>。サービス層や画面に
 * 漏らすと、画面と経路探索が別々の答えを出すようになる。
 *
 * <p>「運べるか」「いつ出るか」は {@link Voyage} に問う。SQL 側で同じ判断を書き直さない
 * （書き直すと、SQL と集約で答えが食い違う。IT3 でそれが起きた）。
 */
public final class TransitPathFinder {

    /**
     * 条件を満たす経路をすべて返す。並び順はここでは決めない（[ADR-018] の推奨順は
     * {@link RouteRecommendation} が与える）。
     *
     * @param specification 探索条件
     * @param voyages 探索の対象とする航海。呼び出し側が事前に絞ってよいが、
     *     <strong>絞りすぎると候補が落ちる</strong>ため、判断はここに任せて広めに渡す
     */
    public List<TransitPath> find(RouteSearchSpecification specification, List<Voyage> voyages) {
        if (specification == null || voyages == null) {
            return List.of();
        }
        List<Voyage> usable = voyages.stream()
                .filter(voyage -> voyage.supports(specification.cargoType()))
                .toList();

        Search search = new Search(specification, usable);
        search.from(new Position(specification.origin(), null, null));
        return List.copyOf(search.found);
    }

    /**
     * 探索のいまの居場所。
     *
     * @param port いまいる港
     * @param readyAt 荷物が引き渡せる時刻（最初の区間では無い）
     * @param arrivedOn ここまで運んできた航海（最初の区間では無い）
     */
    private record Position(Location port, Instant readyAt, VoyageNumber arrivedOn) {
    }

    /**
     * 1 回の探索。条件と対象は変わらないので、たどっている途中の状態だけを持つ。
     *
     * <p>再帰の引数に条件・対象・結果まで並べると、読むほうは「どれが変わるのか」を
     * 追えなくなる。変わるのは居場所と、いま組み立てている経路だけである。
     */
    private final class Search {

        private final RouteSearchSpecification specification;
        private final List<Voyage> voyages;
        private final Deque<TransitEdge> current = new ArrayDeque<>();
        private final Set<Location> visited = new HashSet<>();
        private final List<TransitPath> found = new ArrayList<>();

        private Search(RouteSearchSpecification specification, List<Voyage> voyages) {
            this.specification = specification;
            this.voyages = voyages;
            this.visited.add(specification.origin());
        }

        /**
         * その港から先をたどる。
         *
         * <p><strong>同じ船に乗り直さない。</strong>途中の寄港地で降りてまた同じ船に乗る経路を
         * 作ると、出発も到着も船も同じ 2 行が並び、片方だけが「積み替え 1 回」として高く見える。
         * 1 本の航海で通しで運べるなら、それは 1 区間で表す。
         *
         * <p><strong>一度通った港へは戻らない。</strong>往復航海があると、素朴な探索は
         * 「東京 → 釜山 → 東京 → ロサンゼルス」のような、行って戻るだけの経路を見つける。
         * 遅くて荷役が増えるだけで、業務としては意味が無い。
         *
         * <p>積み替えの上限による打ち切りは<strong>候補の集合を変えない</strong>
         * （同じ判断を {@link RouteSearchSpecification#isSatisfiedBy} が最後に必ず行う）。
         * ここで打ち切るのは、深い経路を作ってから捨てる無駄を避けるためである。
         * 業務上の上限を守っているのは条件側であり、ここではない。
         */
        private void from(Position position) {
            if (current.size() > specification.maxTransshipments()) {
                return;
            }
            for (Voyage voyage : voyages) {
                if (voyage.voyageNumber().equals(position.arrivedOn())) {
                    continue;
                }
                for (TransitEdge edge : departuresFrom(voyage, position)) {
                    follow(edge);
                }
            }
        }

        /** その区間をたどる。目的地に着いたら候補にし、途中なら先へ進む。 */
        private void follow(TransitEdge edge) {
            if (edge.to().equals(specification.destination())) {
                collect(edge);
                return;
            }
            if (visited.contains(edge.to())) {
                return;
            }
            current.addLast(edge);
            visited.add(edge.to());
            from(new Position(edge.to(), edge.arrivalTime(), edge.voyageNumber()));
            visited.remove(edge.to());
            current.removeLast();
        }

        /** 目的地までつながったので、条件を満たすなら候補にする。 */
        private void collect(TransitEdge lastLeg) {
            current.addLast(lastLeg);
            TransitPath path = TransitPath.of(List.copyOf(current));
            if (specification.isSatisfiedBy(path)) {
                found.add(path);
            }
            current.removeLast();
        }

        /**
         * その港から乗れる区間を挙げる。
         *
         * <p>寄港位置は往復航海のためにすべて見る。同じ港に 2 度寄る航海では、往路と復路で
         * 別の区間になる。
         */
        private List<TransitEdge> departuresFrom(Voyage voyage, Position position) {
            List<TransitEdge> edges = new ArrayList<>();
            for (int loadOrder : voyage.callingOrdersOf(position.port())) {
                Instant departure = voyage.departureTimeAt(loadOrder).orElse(null);
                if (departure == null || !readyForTransshipment(position.readyAt(), departure)) {
                    continue;
                }
                edges.addAll(arrivalsAfter(voyage, position.port(), loadOrder, departure));
            }
            return edges;
        }

        /** その寄港位置から先で降りられる港を挙げる。 */
        private List<TransitEdge> arrivalsAfter(Voyage voyage, Location from, int loadOrder,
                Instant departure) {
            List<TransitEdge> edges = new ArrayList<>();
            for (int unloadOrder = loadOrder + 1;
                    voyage.arrivalTimeAt(unloadOrder).isPresent(); unloadOrder++) {
                Instant arrival = voyage.arrivalTimeAt(unloadOrder).orElseThrow();
                Location to = voyage.schedule().callingPorts().get(unloadOrder);
                if (!arrival.isAfter(specification.arrivalDeadline()) && !to.equals(from)) {
                    edges.add(TransitEdge.of(voyage.voyageNumber(), voyage.vesselName(),
                            voyage.carrierName(), from, to, departure, arrival));
                }
            }
            return edges;
        }

        /**
         * 積み替えが間に合うか。
         *
         * <p>最初の区間（{@code readyAt} が無い）には積み替えが無い。判断は
         * {@link TransitPath#MINIMUM_TRANSSHIPMENT} と共有する（探索側に書き直さない）。
         */
        private boolean readyForTransshipment(Instant readyAt, Instant departure) {
            return readyAt == null
                    || !departure.isBefore(readyAt.plus(TransitPath.MINIMUM_TRANSSHIPMENT));
        }
    }
}
