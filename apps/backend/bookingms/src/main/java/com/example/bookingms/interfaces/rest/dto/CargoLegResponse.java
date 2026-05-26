package com.example.bookingms.interfaces.rest.dto;

import com.example.bookingms.domain.projections.CargoLeg;

import java.time.LocalDateTime;

/**
 * 確定旅程の輸送区間応答 DTO（US11 / US12、GET /api/v1/bookings/{id}/route）。
 *
 * <p>予約詳細（S10）で確定経路の経由港・航海番号・日時を表示する。</p>
 */
public record CargoLegResponse(
        int legSeq,
        String voyageNumber,
        String loadUnlocode,
        String unloadUnlocode,
        LocalDateTime loadAt,
        LocalDateTime unloadAt
) {
    public static CargoLegResponse from(CargoLeg leg) {
        return new CargoLegResponse(
                leg.getLegSeq(),
                leg.getVoyageNumber(),
                leg.getLoadUnlocode(),
                leg.getUnloadUnlocode(),
                leg.getLoadAt(),
                leg.getUnloadAt());
    }
}
