package com.example.bookingms.interfaces.rest;

import com.example.bookingms.application.internal.EstimateQuote;
import java.util.List;

/**
 * 候補の試算結果（受入基準 01-2・01-3・01-5）。
 *
 * <p><strong>「候補が 0 件」と「間に合う候補が 0 件」を区別する。</strong>
 * 後者は「最短でも N 日超過します」と言える——荷主に折り返す言葉があるかどうかが違う。
 *
 * @param candidates 期限に間に合う候補（推奨順）
 * @param daysExceeded 間に合う候補が無いとき、最短でも何日超過するか
 */
public record EstimateQuoteResponse(
        List<EstimateResponse.RouteCandidateResponse> candidates, Integer daysExceeded) {

    public static EstimateQuoteResponse from(EstimateQuote quote) {
        return new EstimateQuoteResponse(quote.candidates().stream()
                .map(candidate -> new EstimateResponse.RouteCandidateResponse(
                        candidate.voyageNumber(), candidate.transitPort(),
                        candidate.transitDays(), candidate.estimatedCost()))
                .toList(), quote.daysExceeded());
    }
}
