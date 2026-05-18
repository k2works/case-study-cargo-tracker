package com.example.cargotracker.handlingms.domain.model.valueobjects;

import java.util.Objects;

/**
 * Booking Context の貨物情報のスナップショット（ACL: 腐敗防止層）。
 *
 * <p>handlingms は bookingms の {@code Cargo} に直接依存せず、本クラスを介して
 * 必要な情報のみを保持する。{@code CargoBookedEvent} / {@code CargoRoutedEvent} を購読して
 * handlingms 内 EventHandler が独自に維持する。</p>
 *
 * <p>関連 ADR: ADR-0012 handlingms と trackingms の責務分離</p>
 *
 * @param bookingId           貨物予約 ID
 * @param trackingNumber      追跡番号（経路確定後に発行される）
 * @param origin              出発地
 * @param destination         到着地
 * @param cargoType           貨物種別（GENERAL / HAZARDOUS / REFRIGERATED）
 */
public record CargoSnapshot(
        String bookingId,
        TrackingNumber trackingNumber,
        Location origin,
        Location destination,
        String cargoType) {

    public CargoSnapshot {
        Objects.requireNonNull(bookingId, "bookingId");
        Objects.requireNonNull(origin, "origin");
        Objects.requireNonNull(destination, "destination");
        Objects.requireNonNull(cargoType, "cargoType");
        // trackingNumber は追跡番号発行前は null になる可能性がある
    }

    /**
     * 作業場所が予定ルート（出発地・到着地）と一致しているかを判定する（H7: 予定外場所検知）。
     *
     * <p>IT5 では出発地・到着地のみを照合する簡易実装。IT6 で {@code itinerary}（経由港） を
     * 加えた完全な判定に拡張予定（domain-model.md の CargoItinerary 連携）。</p>
     *
     * @param type     荷役作業種別
     * @param location 実際の作業場所
     * @return 予定通りの場所であれば {@code true}
     */
    public boolean isExpectedHandling(HandlingType type, Location location) {
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(location, "location");
        return switch (type) {
            case RECEIVE -> origin.unLocode().equals(location.unLocode());
            case CLAIM, CUSTOMS -> destination.unLocode().equals(location.unLocode());
            // LOAD/UNLOAD は経由港の可能性があるため、IT5 では原則 true とする（将来拡張）
            case LOAD, UNLOAD -> true;
        };
    }
}
