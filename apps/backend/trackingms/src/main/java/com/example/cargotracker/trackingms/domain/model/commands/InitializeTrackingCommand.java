package com.example.cargotracker.trackingms.domain.model.commands;

import com.example.cargotracker.trackingms.domain.model.valueobjects.CargoItinerary;
import com.example.cargotracker.trackingms.domain.model.valueobjects.Location;
import java.time.LocalDateTime;
import java.util.Objects;
import org.axonframework.modelling.annotation.TargetEntityId;

/**
 * 追跡集約初期化コマンド（US18）。
 *
 * <p>bookingms の {@code CargoTrackedEvent} 購読を契機に
 * 内部で発行される。これにより {@code TrackingActivity} が
 * {@link com.example.cargotracker.trackingms.domain.model.valueobjects.TransportStatus#NOT_RECEIVED}
 * 状態で生成され、以降の状態遷移を Event Sourcing で追跡可能となる。</p>
 *
 * @param trackingNumber    追跡番号（Aggregate 識別子）
 * @param bookingId         予約 ID
 * @param itinerary         確定経路
 * @param origin            出発地
 * @param destination       到着地
 * @param estimatedArrival  推定到着日時（経路最終 unloadTime）
 */
public record InitializeTrackingCommand(
        @TargetEntityId String trackingNumber,
        String bookingId,
        CargoItinerary itinerary,
        Location origin,
        Location destination,
        LocalDateTime estimatedArrival) {

    public InitializeTrackingCommand {
        Objects.requireNonNull(trackingNumber, "trackingNumber");
        Objects.requireNonNull(bookingId, "bookingId");
        Objects.requireNonNull(itinerary, "itinerary");
        Objects.requireNonNull(origin, "origin");
        Objects.requireNonNull(destination, "destination");
        Objects.requireNonNull(estimatedArrival, "estimatedArrival");
    }
}
