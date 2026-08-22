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
import java.time.Clock;
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
    private final Clock clock;

    public FindRouteCandidatesUseCase(VoyageRepository voyages, LocationRepository locations,
            ZoneId businessZone, Clock clock) {
        this.voyages = voyages;
        this.locations = locations;
        this.finder = new TransitPathFinder();
        this.businessZone = businessZone;
        this.clock = clock;
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
    /**
     * 出発希望日を指定して候補を算出する（US10・残作業 5）。
     *
     * @param earliestDeparture 荷物が出せるようになる日。<strong>日付で受け取る</strong>。
     *     「9 月 1 日以降でないと倉庫に入らない」は、業務タイムゾーンでのその日の始まりを
     *     境目とする。指定が無ければ出発の早さでは絞らない
     */
    public Result find(String originUnLocode, String destinationUnLocode,
            LocalDate arrivalDeadline, CargoType cargoType, Integer maxTransshipments,
            LocalDate earliestDeparture) {
        Location origin = requireLocation(originUnLocode, "出発地");
        Location destination = requireLocation(destinationUnLocode, "目的地");
        if (arrivalDeadline == null) {
            throw new IllegalArgumentException("到着期限を指定してください");
        }
        if (cargoType == null) {
            throw new IllegalArgumentException("貨物種別を指定してください");
        }

        // 期限は**目的地の暦**で判断する（ADR-010）。単一の業務タイムゾーンで判断すると、
        // 目的地が東西にずれた分だけ bookingms の判定と食い違い、こちらが候補に出した経路を
        // 向こうが「期限を過ぎている」と断る（またはその逆で正当な便が消える）
        ZoneId destinationZone = locations.timeZoneOf(destinationUnLocode).orElse(businessZone);
        RouteSearchSpecification specification = RouteSearchSpecification.of(
                origin, destination, endOfDay(arrivalDeadline, destinationZone), cargoType,
                maxTransshipments == null
                        ? RouteSearchSpecification.DEFAULT_MAX_TRANSSHIPMENTS
                        : maxTransshipments,
                startOfDay(earliestDeparture));

        // すでに出てしまった船は押さえられない。航海スケジュールの一覧と同じ扱いにする。
        // 出発希望日がそれより後なら、そちらを境目にする（前の便を引いても捨てるだけ）
        Instant notDepartedBefore = specification.earliestDeparture() == null
                ? clock.instant()
                : maxOf(clock.instant(), specification.earliestDeparture());
        List<Voyage> searchable = voyages.findCandidates(specification, notDepartedBefore);
        List<TransitPath> found = finder.find(specification, searchable);
        return new Result(RouteRecommendation.rank(found), specification);
    }

    /**
     * 期限の日付を、業務タイムゾーンでのその日の終わりに直す。
     *
     * <p>UTC で判断すると、時差の分だけ「当日」が短くなり、当日の遅い時刻に着く便が
     * 黙って候補から消える。日中しか動かさないと気づかない。
     *
     * <p><strong>使うのは目的地の暦である</strong>（[ADR-010]）。bookingms も同じ規則で
     * 割り当ての可否を判定するため、片方が単一の業務タイムゾーンを使うと判定が食い違う。
     */
    private static Instant endOfDay(LocalDate date, ZoneId zone) {
        return date.plusDays(1).atStartOfDay(zone).toInstant().minusNanos(1);
    }

    /** 出発希望日を、業務タイムゾーンでのその日の始まりに直す。指定が無ければ {@code null}。 */
    private Instant startOfDay(LocalDate date) {
        return date == null ? null : date.atStartOfDay(businessZone).toInstant();
    }

    private static Instant maxOf(Instant one, Instant other) {
        return one.isAfter(other) ? one : other;
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
