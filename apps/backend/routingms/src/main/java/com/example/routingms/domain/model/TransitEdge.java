package com.example.routingms.domain.model;

import com.example.shared.domain.model.Location;
import java.time.Instant;

/**
 * 経路区間。経路候補のうち、1 つの航海で運ばれる 1 区間（US08）。
 *
 * <p>Booking Context の {@code Leg}（輸送区間）とは<strong>別の型</strong>である。
 * Leg は確定した予約に紐づく記録であり、こちらは確定前の探索結果にすぎない。
 * 変換は US09 の ACL で行い、bookingms 側の型をここへ持ち込まない（BC 独立性）。
 */
public final class TransitEdge {

    private final VoyageNumber voyageNumber;
    private final Location from;
    private final Location to;
    private final Instant departureTime;
    private final Instant arrivalTime;

    private TransitEdge(VoyageNumber voyageNumber, Location from, Location to,
            Instant departureTime, Instant arrivalTime) {
        this.voyageNumber = voyageNumber;
        this.from = from;
        this.to = to;
        this.departureTime = departureTime;
        this.arrivalTime = arrivalTime;
    }

    /** 新規に組み立てる。ここでだけ検査する。 */
    public static TransitEdge of(VoyageNumber voyageNumber, Location from, Location to,
            Instant departureTime, Instant arrivalTime) {
        if (voyageNumber == null) {
            throw new IllegalArgumentException("どの航海で運ぶかは必須です");
        }
        if (from == null || to == null) {
            throw new IllegalArgumentException("区間の出発地と到着地は必須です");
        }
        if (from.equals(to)) {
            throw new IllegalArgumentException("区間の出発地と到着地は同じにできません");
        }
        if (departureTime == null || arrivalTime == null) {
            throw new IllegalArgumentException("区間の出発日時と到着日時は必須です");
        }
        if (!arrivalTime.isAfter(departureTime)) {
            throw new IllegalArgumentException("到着日時は出発日時より後にしてください");
        }
        return new TransitEdge(voyageNumber, from, to, departureTime, arrivalTime);
    }

    public VoyageNumber voyageNumber() {
        return voyageNumber;
    }

    public Location from() {
        return from;
    }

    public Location to() {
        return to;
    }

    public Instant departureTime() {
        return departureTime;
    }

    public Instant arrivalTime() {
        return arrivalTime;
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof TransitEdge edge
                && voyageNumber.equals(edge.voyageNumber)
                && from.equals(edge.from)
                && to.equals(edge.to)
                && departureTime.equals(edge.departureTime)
                && arrivalTime.equals(edge.arrivalTime);
    }

    @Override
    public int hashCode() {
        return java.util.Objects.hash(voyageNumber, from, to, departureTime, arrivalTime);
    }

    @Override
    public String toString() {
        return "%s %s→%s %s/%s".formatted(voyageNumber.value(), from.unLocode(), to.unLocode(),
                departureTime, arrivalTime);
    }
}
