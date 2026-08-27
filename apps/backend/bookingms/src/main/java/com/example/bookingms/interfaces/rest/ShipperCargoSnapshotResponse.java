package com.example.bookingms.interfaces.rest;

import com.example.bookingms.domain.model.BookingId;
import com.example.bookingms.domain.model.Cargo;
import com.example.bookingms.domain.model.TrackingNumber;

/**
 * trackingms が荷主向け追跡境界を判定するための Snapshot（US33）。
 *
 * <p>handlingms 用の {@link CargoSnapshotResponse} とは分ける。荷主 ID は自社境界の判定に
 * 必要だが、荷役の照合には不要である。
 */
public record ShipperCargoSnapshotResponse(String bookingId, String trackingNumber,
        Long shipperId) {

    public static ShipperCargoSnapshotResponse from(Cargo cargo) {
        return new ShipperCargoSnapshotResponse(
                cargo.bookingId().map(BookingId::value).orElse(null),
                cargo.trackingNumber().map(TrackingNumber::value).orElse(null),
                cargo.shipperId());
    }
}
