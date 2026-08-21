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
     * 積み替え回数の既定の上限（[ADR-018] の決定 5）。
     *
     * <p>荷役のたびに損傷と遅延の危険が上がるため、既定では 2 回までを候補にする。
     */
    public static final int DEFAULT_MAX_TRANSSHIPMENTS = 2;

    /**
     * 積み替え回数の<strong>絶対の上限</strong>（[ADR-018] の決定 5）。
     *
     * <p>経路設計者は候補が無いときに上限を緩められるが、いくらでも緩められてはいけない。
     * 探索は深さに対して指数的に広がるため、上限を外から任意に上げられると、1 回の
     * 問い合わせでサービスを止められる。業務としても、4 回以上の積み替えを提案する場面が無い。
     */
    public static final int ABSOLUTE_MAX_TRANSSHIPMENTS = 3;

    /** @deprecated 既定値と絶対上限を区別したため {@link #DEFAULT_MAX_TRANSSHIPMENTS} を使う。 */
    @Deprecated(forRemoval = true)
    public static final int MAX_TRANSSHIPMENTS = DEFAULT_MAX_TRANSSHIPMENTS;

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
        return of(origin, destination, arrivalDeadline, cargoType, DEFAULT_MAX_TRANSSHIPMENTS);
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
        if (maxTransshipments > ABSOLUTE_MAX_TRANSSHIPMENTS) {
            throw new IllegalArgumentException(
                    "積み替えの上限は %d 回までにしてください".formatted(ABSOLUTE_MAX_TRANSSHIPMENTS));
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
