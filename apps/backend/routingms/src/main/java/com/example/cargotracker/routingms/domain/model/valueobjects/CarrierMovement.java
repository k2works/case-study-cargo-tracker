package com.example.cargotracker.routingms.domain.model.valueobjects;

import java.time.LocalDateTime;
import java.util.Objects;

/**
 * 寄港地の出発・到着情報（CarrierMovement）。
 *
 * <p>Voyage Aggregate の構成要素。順序付きリストで保持される。</p>
 *
 * <p>不変条件: arrivalTime &gt; departureTime（同一寄港間の時刻整合性）</p>
 */
public record CarrierMovement(
        UnLocode departure,
        UnLocode arrival,
        LocalDateTime departureTime,
        LocalDateTime arrivalTime) {

    public CarrierMovement {
        Objects.requireNonNull(departure, "departure");
        Objects.requireNonNull(arrival, "arrival");
        Objects.requireNonNull(departureTime, "departureTime");
        Objects.requireNonNull(arrivalTime, "arrivalTime");
        if (departure.equals(arrival)) {
            throw new IllegalArgumentException("departure と arrival は同一にできません: " + departure.value());
        }
        if (!arrivalTime.isAfter(departureTime)) {
            throw new IllegalArgumentException("arrivalTime は departureTime より後である必要があります: departure="
                    + departureTime + " arrival=" + arrivalTime);
        }
    }
}
