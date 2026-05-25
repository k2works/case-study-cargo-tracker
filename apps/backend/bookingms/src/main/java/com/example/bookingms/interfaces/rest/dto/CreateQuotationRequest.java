package com.example.bookingms.interfaces.rest.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * 輸送見積作成リクエスト（US01）。
 *
 * <p>{@code candidates} はフロントエンドが US07 航海検索結果から選んだルート候補。
 * 概算金額は候補の最安費用をサーバー側で算出する。{@code quotationId} 省略時はサーバーで採番する。</p>
 */
public record CreateQuotationRequest(
        String quotationId,
        String shipperId,
        String originUnlocode,
        String destinationUnlocode,
        LocalDate arrivalDeadline,
        String cargoType,
        BigDecimal weightKg,
        String productName,
        LocalDate validUntil,
        List<CandidateInput> candidates
) {
    public record CandidateInput(
            String itinerarySummary,
            int estimatedDays,
            BigDecimal estimatedCost,
            String estimatedCurrency
    ) {
    }
}
