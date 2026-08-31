package com.example.bookingms.interfaces.rest;

import com.example.bookingms.domain.model.valueobjects.BookingId;
import com.example.bookingms.domain.model.aggregates.Cargo;
import com.example.bookingms.domain.model.valueobjects.TrackingNumber;

/**
 * trackingms が荷主向け追跡境界を判定するための Snapshot（US33）。
 *
 * <p>handlingms 用の {@link CargoSnapshotResponse} とは分ける。荷主 ID は自社境界の判定に
 * 必要だが、荷役の照合には不要である。
 */
public record ShipperCargoSnapshotResponse(String bookingId, String trackingNumber,
        Long shipperId, boolean simulated) {

    /**
     * 由来（{@code simulated}）は荷主コードの帯で決まる（[ADR-030] 決定 3）。
     *
     * <p><strong>コードそのものは返さない。</strong>追跡側に荷主の採番規則を知らせる
     * 必要はなく、知らせると帯を変えたときに両方を直すことになる。
     */
    public static ShipperCargoSnapshotResponse from(
            com.example.bookingms.domain.repository.CargoSummary summary) {
        Cargo cargo = summary.cargo();
        return new ShipperCargoSnapshotResponse(
                cargo.bookingId().map(BookingId::value).orElse(null),
                cargo.trackingNumber().map(TrackingNumber::value).orElse(null),
                cargo.shipperId(),
                summary.simulated());
    }
}
