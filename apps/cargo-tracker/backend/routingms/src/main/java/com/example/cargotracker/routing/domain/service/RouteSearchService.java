package com.example.cargotracker.routing.domain.service;

import com.example.cargotracker.routing.domain.model.valueobjects.RouteSearchSpecification;
import com.example.cargotracker.routing.domain.model.valueobjects.TransitEdge;
import com.example.cargotracker.routing.domain.model.valueobjects.TransitPath;
import com.example.cargotracker.shared.domain.location.Location;
import java.time.ZoneId;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 経路候補を探す（US08 / UC06）。
 *
 * <p><b>ドメインサービスに置く。</b> 集約境界（{@code Voyage}）を越えるグラフ探索なので、
 * どの集約の責務にもならない（domain-model.md）。状態を変えないので Query 側に置く。</p>
 *
 * <p><b>候補 0 件は例外にしない。</b> 「期限内に着ける経路が無い」は業務として起こる
 * ことで、失敗ではない。「探索できなかった」と言い分けるのは呼ぶ側の責務である
 * （黙って 0 件にすると、条件を変え続ける羽目になる）。</p>
 *
 * <p><b>打ち切りは業務の判断である</b>（[ADR-0007]）。無制限に探すと、航海が増えたときに
 * 組合せが爆発して応答が返らない。上限に当たったことは {@link #truncated} で分かる。
 * 黙って切ると「候補が無い」と読まれる。</p>
 */
public class RouteSearchService {

    /** 乗り継ぎ回数の上限（ADR-0007）。区間数はこれ + 1 まで。 */
    public static final int MAX_TRANSFERS = 3;

    /** 返す候補数の上限（ADR-0007）。 */
    public static final int MAX_CANDIDATES = 20;

    private final ZoneId businessZone;

    /**
     * @param businessZone 期限を日付で比べるときのタイムゾーン。UTC で判断すると、
     *     時差の分だけ「期限当日」がずれる
     */
    public RouteSearchService(ZoneId businessZone) {
        this.businessZone = businessZone;
    }

    /**
     * 推奨順の候補。
     *
     * <p>並びは<b>直行便が先、そのあと所要の短い順</b>（受入基準 4・5）。直行便は
     * 積み替えが無いぶん事故と遅延の芽が少ないので、多少遅くても優先する。</p>
     */
    public List<TransitPath> findCandidates(RouteSearchSpecification specification,
            VoyageGraph graph) {
        return search(specification, graph).candidates();
    }

    /**
     * 候補と、打ち切りに当たったかどうか。
     *
     * <p><b>打ち切りを候補の件数だけで判断しない。</b> 乗り継ぎの上限で捨てた枝は
     * 件数に現れないので、上限を超える経路しか無い予約は「候補 0 件」と同じ見え方に
     * なる。条件を変えても直らないものを、変え続けさせることになる。</p>
     */
    public RouteSearchResult search(RouteSearchSpecification specification, VoyageGraph graph) {
        List<TransitPath> found = new ArrayList<>();
        boolean depthLimited = false;
        // 幅優先。深さ優先だと、上限に当たるまでの探索が 1 本の枝に偏る。
        Deque<List<TransitEdge>> frontier = new ArrayDeque<>();
        frontier.add(List.of());

        while (!frontier.isEmpty() && found.size() < MAX_CANDIDATES) {
            List<TransitEdge> path = frontier.poll();
            Location at = path.isEmpty()
                    ? specification.searchOrigin()
                    : path.get(path.size() - 1).unload();

            if (path.size() > MAX_TRANSFERS) {
                // ここで捨てた枝は、探せば目的地に着いたかもしれない。
                depthLimited = true;
                continue;
            }
            for (TransitEdge edge : graph.edgesFrom(at)) {
                if (!connects(path, edge, specification, graph)) {
                    continue;
                }
                List<TransitEdge> extended = new ArrayList<>(path);
                extended.add(edge);

                if (edge.unload().equals(specification.destination())) {
                    TransitPath candidate = new TransitPath(extended);
                    if (candidate.meetsDeadline(specification, businessZone)) {
                        found.add(candidate);
                    }
                    // 目的地に着いた経路をさらに伸ばさない。通り過ぎる経路は候補にしない。
                    continue;
                }
                frontier.add(extended);
            }
        }

        List<TransitPath> ordered = found.stream()
                .sorted(Comparator.comparing((TransitPath p) -> !p.isDirect())
                        .thenComparing(TransitPath::totalDuration))
                .limit(MAX_CANDIDATES)
                .toList();
        return new RouteSearchResult(ordered,
                depthLimited || ordered.size() >= MAX_CANDIDATES);
    }

    /**
     * 探索の結果。
     *
     * @param candidates 推奨順の候補（0 件でも例外にしない）
     * @param truncated 上限（乗り継ぎ回数・候補数）で探索を切ったか。
     *     <b>画面に出す。</b> 黙って切ると「候補が無い」と読まれる
     */
    public record RouteSearchResult(List<TransitPath> candidates, boolean truncated) {
    }

    /** その区間に乗れるか。港・貨物種別・時刻の連結・同じ港の通過を見る。 */
    private boolean connects(List<TransitEdge> path, TransitEdge edge,
            RouteSearchSpecification specification, VoyageGraph graph) {
        if (!specification.allows(edge.unload())) {
            return false;
        }
        if (!specification.acceptedBy(graph.acceptedCargoTypes(edge.voyageNumber()))) {
            return false;
        }
        if (!path.isEmpty()
                && edge.loadTime().isBefore(path.get(path.size() - 1).unloadTime())) {
            return false;
        }
        // 同じ港を 2 度通らない。戻る経路は所要が伸びるだけで、探索は無限に伸びる。
        Set<Location> visited = new HashSet<>();
        visited.add(specification.searchOrigin());
        for (TransitEdge previous : path) {
            visited.add(previous.unload());
        }
        return !visited.contains(edge.unload());
    }
}
