package com.example.routingms.domain.model;

import com.example.shared.domain.model.Location;
import java.time.Instant;
import java.util.Objects;

/**
 * 運送区間。1 つの港から次の港までの移動を表す。
 */
public final class CarrierMovement {

    private final Location departureLocation;
    private final Location arrivalLocation;
    private final Instant departureTime;
    private final Instant arrivalTime;

    private CarrierMovement(Location departureLocation, Location arrivalLocation,
            Instant departureTime, Instant arrivalTime) {
        this.departureLocation = departureLocation;
        this.arrivalLocation = arrivalLocation;
        this.departureTime = departureTime;
        this.arrivalTime = arrivalTime;
    }

    /** 新規に受け入れる。ここでだけ検査する。 */
    public static CarrierMovement of(Location departureLocation, Location arrivalLocation,
            Instant departureTime, Instant arrivalTime) {
        if (departureLocation == null || arrivalLocation == null) {
            throw new IllegalArgumentException("区間の出発地と到着地は必須です");
        }
        if (departureLocation.equals(arrivalLocation)) {
            throw new IllegalArgumentException("区間の出発地と到着地は同じにできません");
        }
        if (departureTime == null || arrivalTime == null) {
            throw new IllegalArgumentException("区間の出発日時と到着日時は必須です");
        }
        if (!arrivalTime.isAfter(departureTime)) {
            // 同時刻は「移動していない」ため受け付けない（境界は VoyageTest で固定）
            throw new IllegalArgumentException("到着日時は出発日時より後にしてください");
        }
        return new CarrierMovement(departureLocation, arrivalLocation, departureTime, arrivalTime);
    }

    /** 永続化された行から復元する。ここでは検査しない。 */
    public static CarrierMovement restore(Location departureLocation, Location arrivalLocation,
            Instant departureTime, Instant arrivalTime) {
        return new CarrierMovement(departureLocation, arrivalLocation, departureTime, arrivalTime);
    }

    public Location departureLocation() {
        return departureLocation;
    }

    public Location arrivalLocation() {
        return arrivalLocation;
    }

    public Instant departureTime() {
        return departureTime;
    }

    public Instant arrivalTime() {
        return arrivalTime;
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof CarrierMovement movement
                && departureLocation.equals(movement.departureLocation)
                && arrivalLocation.equals(movement.arrivalLocation)
                && departureTime.equals(movement.departureTime)
                && arrivalTime.equals(movement.arrivalTime);
    }

    @Override
    public int hashCode() {
        return Objects.hash(departureLocation, arrivalLocation, departureTime, arrivalTime);
    }
}
