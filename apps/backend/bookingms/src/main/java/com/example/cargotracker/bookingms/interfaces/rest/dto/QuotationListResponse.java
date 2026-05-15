package com.example.cargotracker.bookingms.interfaces.rest.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 見積一覧レスポンス DTO（US01 S02）。
 *
 * <p>一覧用に軽量化。候補リストや危険物詳細は詳細画面（{@link QuotationResponse}）で取得する。</p>
 */
public record QuotationListResponse(
        String quotationId,
        Long shipperId,
        String originUnLocode,
        String destinationUnLocode,
        LocalDate arrivalDeadline,
        String cargoType,
        BigDecimal weightKg,
        BigDecimal estimatedAmount,
        String estimatedCurrency,
        LocalDate validUntil,
        String status) {
}
