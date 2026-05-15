package com.example.cargotracker.routingms.interfaces.rest.dto;

import java.time.LocalDateTime;

/**
 * 航海スケジュール一覧取得のレスポンス DTO。
 *
 * <p>{@code voyage} Read Model の射影。寄港地（{@code carrier_movement}）と
 * 対応貨物種別（{@code voyage_accepted_cargo_type}）は詳細 API（IT3 以降）で返す。</p>
 */
public record VoyageListResponse(
        String voyageNumber,
        String carrierCode,
        String carrierName,
        String shipName,
        String originUnLocode,
        String destinationUnLocode,
        LocalDateTime departureDate,
        LocalDateTime arrivalDate,
        String status) {
}
