package com.example.bookingms.domain.model;

import java.math.BigDecimal;

/**
 * 見積のルート候補（US01 / Booking Context）。
 *
 * <p>data-model の {@code quotation_candidate} テーブルに対応する。
 * 経路の要約・所要日数・概算費用を保持する。</p>
 */
public record RouteCandidate(
        String itinerarySummary,
        int estimatedDays,
        BigDecimal estimatedCost,
        String estimatedCurrency
) {
}
