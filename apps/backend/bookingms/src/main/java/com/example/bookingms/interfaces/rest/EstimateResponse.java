package com.example.bookingms.interfaces.rest;

import com.example.bookingms.domain.model.Estimate;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * 見積（US01）。
 *
 * @param estimateId 識別子（UUID）。**URL に出る**
 * @param estimateNumber 見積番号。**荷主と読み合わせる**（受入基準 01-4）
 * @param originUnLocode 出発地
 * @param destinationUnLocode 目的地
 * @param arrivalDeadline 希望期限
 * @param cargoType 貨物種別
 * @param weightKg 重量
 * @param status 見積の状態
 * @param statusLabel 状態の表示名
 * @param candidates ルート候補（推奨順）
 */
public record EstimateResponse(String estimateId, String estimateNumber, String originUnLocode,
        String destinationUnLocode, LocalDate arrivalDeadline, String cargoType,
        BigDecimal weightKg, String status, String statusLabel,
        List<RouteCandidateResponse> candidates) {

    /**
     * ルート候補（受入基準 01-3）。
     *
     * <p><strong>4 項目を返す</strong>——航海番号・経由港・所要日数・概算料金。
     * 1 つ欠けても字面は満たす。
     */
    public record RouteCandidateResponse(String voyageNumber, String transitPort, int transitDays,
            BigDecimal estimatedCost) {
    }

    public static EstimateResponse from(Estimate estimate) {
        return new EstimateResponse(
                estimate.estimateId().value().toString(),
                estimate.estimateNumber().value(),
                estimate.originUnLocode(),
                estimate.destinationUnLocode(),
                estimate.arrivalDeadline(),
                estimate.cargoType().name(),
                estimate.weightKg(),
                estimate.status().name(),
                estimate.status().label(),
                estimate.candidates().stream()
                        .map(candidate -> new RouteCandidateResponse(candidate.voyageNumber(),
                                candidate.transitPort(), candidate.transitDays(),
                                candidate.estimatedCost()))
                        .toList());
    }
}
