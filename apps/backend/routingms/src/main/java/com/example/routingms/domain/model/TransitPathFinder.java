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

        List<TransitPath> found = new ArrayList<>();
        Deque<TransitEdge> current = new ArrayDeque<>();
        Set<Location> visited = new HashSet<>();
        visited.add(specification.origin());

        explore(specification, usable, specification.origin(), null, null, current, visited, found);
        return List.copyOf(found);
    }

    /**
     * 深さ優先で港をたどる。
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
     * 業務上の「3 回以上は候補にしない」を守っているのは条件側であり、ここではない。
     */
    private void explore(RouteSearchSpecification specification, List<Voyage> voyages,
            Location from, Instant readyAt, VoyageNumber arrivedOn, Deque<TransitEdge> current,
            Set<Location> visited, List<TransitPath> found) {
        if (current.size() > specification.maxTransshipments()) {
            return;
        }
        for (Voyage voyage : voyages) {
            // 同じ船に乗り直すのは積み替えではない。1 本の航海で通しで運べるなら 1 区間で表す
            if (voyage.voyageNumber().equals(arrivedOn)) {
                continue;
            }
            for (TransitEdge edge : departuresFrom(voyage, from, readyAt, specification)) {
                if (edge.to().equals(specification.destination())) {
                    current.addLast(edge);
                    TransitPath path = TransitPath.of(List.copyOf(current));
                    if (specification.isSatisfiedBy(path)) {
                        found.add(path);
                    }
                    current.removeLast();
                    continue;
                }
                if (visited.contains(edge.to())) {
                    continue;
                }
                current.addLast(edge);
                visited.add(edge.to());
                explore(specification, voyages, edge.to(), edge.arrivalTime(),
                        edge.voyageNumber(), current, visited, found);
                visited.remove(edge.to());
                current.removeLast();
            }
        }
    }

    /**
     * その港から乗れる区間を挙げる。
     *
     * <p>寄港位置は往復航海のためにすべて見る。同じ港に 2 度寄る航海では、往路と復路で
     * 別の区間になる。
     */
    private List<TransitEdge> departuresFrom(Voyage voyage, Location from, Instant readyAt,
            RouteSearchSpecification specification) {
        List<TransitEdge> edges = new ArrayList<>();
        for (int loadOrder : voyage.callingOrdersOf(from)) {
            Instant departure = voyage.departureTimeAt(loadOrder).orElse(null);
            if (departure == null || !readyForTransshipment(readyAt, departure)) {
                continue;
            }
            for (int unloadOrder = loadOrder + 1;
                    voyage.arrivalTimeAt(unloadOrder).isPresent(); unloadOrder++) {
                Instant arrival = voyage.arrivalTimeAt(unloadOrder).orElseThrow();
                if (arrival.isAfter(specification.arrivalDeadline())) {
                    continue;
                }
                Location to = voyage.schedule().callingPorts().get(unloadOrder);
                if (to.equals(from)) {
                    continue;
                }
                edges.add(TransitEdge.of(voyage.voyageNumber(), from, to, departure, arrival));
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
