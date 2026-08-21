package com.example.routingms.application.internal;

import com.example.routingms.application.port.LocationRepository;
import com.example.routingms.application.port.VoyageRepository;
import com.example.routingms.domain.model.CargoType;
import com.example.routingms.domain.model.RouteRecommendation;
import com.example.routingms.domain.model.RouteSearchSpecification;
import com.example.routingms.domain.model.TransitPath;
import com.example.routingms.domain.model.TransitPathFinder;
import com.example.routingms.domain.model.Voyage;
import com.example.shared.domain.model.Location;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

/**
 * 経路候補算出（US08）。
 *
 * <p>ここがやるのは 3 つだけである。画面が送った条件をドメインの言葉に直し、探索の対象を
 * 引き、結果を推奨順に並べる。**制約の判断はドメインにある**（ここに書き足すと、画面と
 * 経路探索が別々の答えを出すようになる）。
 */
public class FindRouteCandidatesUseCase {

    private final VoyageRepository voyages;
    private final LocationRepository locations;
    private final TransitPathFinder finder;
    private final ZoneId businessZone;

    public FindRouteCandidatesUseCase(VoyageRepository voyages, LocationRepository locations,
            ZoneId businessZone) {
        this.voyages = voyages;
        this.locations = locations;
        this.finder = new TransitPathFinder();
        this.businessZone = businessZone;
    }

    /**
     * @param candidates 推奨順の候補
     * @param specification 実際に使った条件（画面が「何で絞ったか」を示すために返す）
     */
    public record Result(List<TransitPath> candidates, RouteSearchSpecification specification) {

        public boolean isEmpty() {
            return candidates.isEmpty();
        }
    }

    /**
     * 候補を算出する。
     *
     * @param originUnLocode 出発地。<strong>任意の地点を指定できる</strong>（貨物の現在地を
     *     起点にした再設計。US28）
     * @param arrivalDeadline 到着期限。<strong>日付で受け取る</strong>。業務上「9 月 30 日まで」は
     *     「30 日中に着けばよい」を意味するため、業務タイムゾーンでのその日の終わりを期限とする
     *     （[ADR-017] の決定 3）
     */
    public Result find(String originUnLocode, String destinationUnLocode,
            LocalDate arrivalDeadline, CargoType cargoType, Integer maxTransshipments) {
        Location origin = requireLocation(originUnLocode, "出発地");
        Location destination = requireLocation(destinationUnLocode, "目的地");
        if (arrivalDeadline == null) {
            throw new IllegalArgumentException("到着期限を指定してください");
        }
        if (cargoType == null) {
            throw new IllegalArgumentException("貨物種別を指定してください");
        }

        RouteSearchSpecification specification = maxTransshipments == null
                ? RouteSearchSpecification.of(origin, destination, endOfDay(arrivalDeadline), cargoType)
                : RouteSearchSpecification.of(origin, destination, endOfDay(arrivalDeadline),
                        cargoType, maxTransshipments);

        List<Voyage> searchable = voyages.findCandidates(specification);
        List<TransitPath> found = finder.find(specification, searchable);
        return new Result(RouteRecommendation.rank(found), specification);
    }

    /**
     * 期限の日付を、業務タイムゾーンでのその日の終わりに直す。
     *
     * <p>UTC で判断すると、時差の分だけ「当日」が短くなり、当日の遅い時刻に着く便が
     * 黙って候補から消える。日中しか動かさないと気づかない。
     */
    private Instant endOfDay(LocalDate date) {
        return date.plusDays(1).atStartOfDay(businessZone).toInstant().minusNanos(1);
    }

    /**
     * 地点は必ずマスタから引く。
     *
     * <p>画面から来た UN/LOCODE をそのまま {@code Location} にすると、存在しない港でも
     * 探索が走り、結果は必ず 0 件になる。経路設計者には「経路が無い」としか見えず、
     * 打ち間違いだと分からない。
     */
    private Location requireLocation(String unLocode, String what) {
        if (unLocode == null || unLocode.isBlank()) {
            throw new IllegalArgumentException("%sを指定してください".formatted(what));
        }
        return locations.findByUnLocode(unLocode)
                .orElseThrow(() -> new IllegalArgumentException(
                        "%sが見つかりません: %s".formatted(what, unLocode)));
    }
}
