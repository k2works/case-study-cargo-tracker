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

    private final Location origin;
    private final Location destination;
    private final Instant arrivalDeadline;
    private final CargoType cargoType;
    private final int maxTransshipments;
    /** 荷物が出せるようになる時刻。指定が無ければ出発の早さでは絞らない。 */
    private final Instant earliestDeparture;
    /**
     * 到着期限で候補を弾くか。
     *
     * <p>誤配のあとの組み直しでは<strong>弾かない</strong>（US28・[ADR-026] 決定 4・5）。
     * 誤配した貨物は遅れているのが普通で、元の期限に間に合う便はまず残っていない。
     * ここで刈ると<strong>組み直す手段そのものが無くなり</strong>、貨物は経路から
     * 外れたまま止まる。超える分は荷主に伝えて判断してもらう。
     */
    private final boolean enforceDeadline;

    private RouteSearchSpecification(Location origin, Location destination, Instant arrivalDeadline,
            CargoType cargoType, int maxTransshipments, Instant earliestDeparture,
            boolean enforceDeadline) {
        this.origin = origin;
        this.destination = destination;
        this.arrivalDeadline = arrivalDeadline;
        this.cargoType = cargoType;
        this.maxTransshipments = maxTransshipments;
        this.earliestDeparture = earliestDeparture;
        this.enforceDeadline = enforceDeadline;
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
        return of(origin, destination, arrivalDeadline, cargoType, maxTransshipments, null);
    }

    /**
     * 出発希望日を指定して条件を組み立てる（US10）。
     *
     * <p>荷主が「9 月 1 日以降でないと倉庫に入らない」と言っているのに、それより前に出る便を
     * 候補に出すと、押さえても積むものがない。{@code null} は「指定なし」であり、
     * 出発の早さでは絞らない。
     */
    public static RouteSearchSpecification of(Location origin, Location destination,
            Instant arrivalDeadline, CargoType cargoType, int maxTransshipments,
            Instant earliestDeparture) {
        return build(origin, destination, arrivalDeadline, cargoType, maxTransshipments,
                earliestDeparture, true);
    }

    /**
     * 誤配のあとの組み直しの条件（US28-4・[ADR-026] 決定 4）。
     *
     * <p><strong>期限では弾かない。</strong>弾くと組み直す手段そのものが無くなり、貨物は
     * 経路から外れたまま止まる。期限そのものは持ったままにする——<strong>超える分を示す</strong>
     * ために要る（決定 5）。出発地・目的地・貨物種別・積み替えの上限は今までどおり効く。
     */
    public static RouteSearchSpecification forReroute(Location origin, Location destination,
            Instant arrivalDeadline, CargoType cargoType, int maxTransshipments,
            Instant earliestDeparture) {
        return build(origin, destination, arrivalDeadline, cargoType, maxTransshipments,
                earliestDeparture, false);
    }

    private static RouteSearchSpecification build(Location origin, Location destination,
            Instant arrivalDeadline, CargoType cargoType, int maxTransshipments,
            Instant earliestDeparture, boolean enforceDeadline) {
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
        if (earliestDeparture != null && earliestDeparture.isAfter(arrivalDeadline)) {
            throw new IllegalArgumentException("出発希望日が到着期限より後になっています");
        }
        return new RouteSearchSpecification(origin, destination, arrivalDeadline, cargoType,
                maxTransshipments, earliestDeparture, enforceDeadline);
    }

    /** 到着期限で候補を弾くか。{@link #forReroute} で組んだ条件だけが {@code false}。 */
    public boolean enforcesDeadline() {
        return enforceDeadline;
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
                && (!enforceDeadline || !path.arrivalTime().isAfter(arrivalDeadline))
                // 出発希望日ちょうどに出る便は満たす。荷物はその日から出せる
                && (earliestDeparture == null
                        || !path.departureTime().isBefore(earliestDeparture))
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

    /** 荷物が出せるようになる時刻。指定が無ければ {@code null}。 */
    public Instant earliestDeparture() {
        return earliestDeparture;
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
