package com.example.bookingms.application.internal.commandservices;

import com.example.bookingms.domain.model.valueobjects.RouteCandidate;
import java.util.List;

/**
 * 見積の試算結果（受入基準 01-2・01-3・01-5）。
 *
 * <p><strong>保存する前の答えである。</strong>営業担当者は候補を見てから作成を決める。
 *
 * <p><strong>「候補が 0 件」と「間に合う候補が 0 件」を区別する</strong>（01-5）。
 * 後者は「最短でも N 日超過します」と言える——荷主に折り返す言葉があるかどうかが違う。
 *
 * @param candidates 期限に間に合う候補（推奨順）
 * @param daysExceeded 間に合う候補が無いとき、最短でも何日超過するか。
 *        間に合う候補があれば {@code null}
 */
public record EstimateQuote(List<RouteCandidate> candidates, Integer daysExceeded) {

    /** 間に合う候補があるか。 */
    public boolean hasCandidates() {
        return !candidates.isEmpty();
    }
}
