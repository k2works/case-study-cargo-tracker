package com.example.cargotracker.billing.application.internal.outboundservices;

import com.example.cargotracker.booking.domain.model.valueobjects.CargoType;

import java.math.BigDecimal;
import java.util.Optional;

/**
 * Billing コンテキストから Booking コンテキストへの ACL クエリポート。
 * 確定済み予約の情報を取得する。
 */
public interface FreightBookingQueryPort {

    /**
     * 確定済み予約を予約 ID で取得する。
     * 予約が存在しない場合、またはステータスが CONFIRMED でない場合は {@link Optional#empty()} を返す。
     *
     * @param bookingId 予約 ID 文字列
     * @return 確定済み予約サマリー
     */
    Optional<FreightBookingSummary> findConfirmedBookingById(String bookingId);

    /**
     * 料金算出に必要な予約情報のサマリー。
     */
    record FreightBookingSummary(
            String bookingId,
            CargoType cargoType,
            BigDecimal weightKg,
            String originLocation,
            String destinationLocation
    ) {}
}
