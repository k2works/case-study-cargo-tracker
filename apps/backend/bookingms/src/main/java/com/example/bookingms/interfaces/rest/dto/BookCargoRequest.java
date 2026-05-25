package com.example.bookingms.interfaces.rest.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 貨物予約登録リクエスト DTO（US04 + US05、POST /api/v1/bookings）。
 *
 * <p>{@code bookingId} は省略可能で、省略時はサーバー側で UUID を採番する。
 * US05 で危険物 (hazard*) / 冷凍 (temperatureMinC / temperatureMaxC) フィールドを追加。</p>
 */
public record BookCargoRequest(
        String bookingId,
        String shipperId,
        String originUnlocode,
        String destinationUnlocode,
        LocalDate arrivalDeadline,
        String cargoType,
        BigDecimal weightKg,
        Integer lengthCm,
        Integer widthCm,
        Integer heightCm,
        Integer quantity,
        String productName,
        String hazardImoClass,
        String hazardUnNumber,
        String hazardDeclaration,
        BigDecimal temperatureMinC,
        BigDecimal temperatureMaxC
) {
    /**
     * US04 互換コンストラクタ（既存テスト互換、固有情報なし）。
     */
    public BookCargoRequest(
            String bookingId,
            String shipperId,
            String originUnlocode,
            String destinationUnlocode,
            LocalDate arrivalDeadline,
            String cargoType,
            BigDecimal weightKg,
            Integer lengthCm,
            Integer widthCm,
            Integer heightCm,
            Integer quantity,
            String productName
    ) {
        this(bookingId, shipperId, originUnlocode, destinationUnlocode, arrivalDeadline,
                cargoType, weightKg, lengthCm, widthCm, heightCm, quantity, productName,
                null, null, null, null, null);
    }
}
