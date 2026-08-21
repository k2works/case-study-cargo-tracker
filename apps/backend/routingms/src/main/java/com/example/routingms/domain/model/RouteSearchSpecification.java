package com.example.routingms.domain.model;

import com.example.shared.domain.model.Location;
import java.time.Instant;
import java.util.Objects;

/**
 * 経路探索条件。どこからどこへ、いつまでに、何を運ぶか（US08）。
 *
 * <p><strong>Booking Context の {@code RouteSpecification}（ルート仕様）とは別の型である。</strong>
 * あちらは予約に永続化される輸送の要件であり、こちらは<strong>その場かぎりの探索条件</strong>で、
 * 貨物種別と積み替えの上限という探索固有の項目を持つ。同じ名前にすると US09 の ACL で
 * 変換の両端が同じ名前になり、どちらの型を触っているか分からなくなるため、名前を分けた。
 */
public final class RouteSearchSpecification {

    /**
     * 積み替え回数の上限。
     *
     * <p>上限が無いと、港を経由し続ける経路を延々と作り、探索が終わらない。業務としても
     * 3 回以上の積み替えは荷役のたびに損傷と遅延の危険が上がるため、候補にしない（[ADR-018]）。
     */
    public static final int MAX_TRANSSHIPMENTS = 2;

    private final Location origin;
    private final Location destination;
    private final Instant arrivalDeadline;
    private final CargoType cargoType;
    private final int maxTransshipments;

    private RouteSearchSpecification(Location origin, Location destination, Instant arrivalDeadline,
            CargoType cargoType, int maxTransshipments) {
        this.origin = origin;
        this.destination = destination;
        this.arrivalDeadline = arrivalDeadline;
        this.cargoType = cargoType;
        this.maxTransshipments = maxTransshipments;
    }

    public static RouteSearchSpecification of(Location origin, Location destination,
            Instant arrivalDeadline, CargoType cargoType) {
        return of(origin, destination, arrivalDeadline, cargoType, MAX_TRANSSHIPMENTS);
    }

    /**
     * 積み替えの上限を指定して条件を組み立てる。
     *
     * <p>候補が出なかったとき、経路設計者が条件を緩められるようにするための入口である
     * （「該当なし」で終わらせない）。
     */
    public static RouteSearchSpecification of(Location origin, Location destination,
            Instant arrivalDeadline, CargoType cargoType, int maxTransshipments) {
        if (origin == null || destination == null) {
            throw new IllegalArgumentException("出発地と目的地は必須です");
        }
        if (origin.equals(destination)) {
            throw new IllegalArgumentException("出発地と目的地は同じにできません");
        }
        if (arrivalDeadline == null) {
            throw new IllegalArgumentException("到着期限は必須です");
        }
        if (cargoType == null) {
            throw new IllegalArgumentException("貨物種別は必須です");
        }
        if (maxTransshipments < 0) {
            throw new IllegalArgumentException("積み替えの上限は 0 以上にしてください");
        }
        return new RouteSearchSpecification(origin, destination, arrivalDeadline, cargoType,
                maxTransshipments);
    }

    /**
     * この条件を満たす経路か。
     *
     * <p><strong>期限ちょうどに着く経路は満たす。</strong>期限は「その時刻までに着けばよい」
     * という約束であり、ちょうど着いた貨物は約束を守っている。ここを「より前」にすると、
     * 期限ちょうどの便だけが黙って候補から消える。
     */
    public boolean isSatisfiedBy(TransitPath path) {
        return path != null
                && origin.equals(path.origin())
                && destination.equals(path.destination())
                && !path.arrivalTime().isAfter(arrivalDeadline)
                && path.transshipmentCount() <= maxTransshipments;
    }

    public Location origin() {
        return origin;
    }

    public Location destination() {
        return destination;
    }

    public Instant arrivalDeadline() {
        return arrivalDeadline;
    }

    public CargoType cargoType() {
        return cargoType;
    }

    public int maxTransshipments() {
        return maxTransshipments;
    }

    /** 積み替えの上限だけを変えた条件。条件を緩めて再算出するときに使う。 */
    public RouteSearchSpecification withMaxTransshipments(int newMax) {
        return of(origin, destination, arrivalDeadline, cargoType, newMax);
    }

    /** 到着期限だけを変えた条件。 */
    public RouteSearchSpecification withArrivalDeadline(Instant newDeadline) {
        return of(origin, destination, newDeadline, cargoType, maxTransshipments);
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof RouteSearchSpecification spec
                && origin.equals(spec.origin)
                && destination.equals(spec.destination)
                && arrivalDeadline.equals(spec.arrivalDeadline)
                && cargoType == spec.cargoType
                && maxTransshipments == spec.maxTransshipments;
    }

    @Override
    public int hashCode() {
        return Objects.hash(origin, destination, arrivalDeadline, cargoType, maxTransshipments);
    }
}
