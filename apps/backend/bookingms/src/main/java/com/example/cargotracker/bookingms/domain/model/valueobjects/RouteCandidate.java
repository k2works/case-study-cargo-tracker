package com.example.cargotracker.bookingms.domain.model.valueobjects;

import java.util.Objects;

/**
 * 見積に含まれるルート候補。
 *
 * <p>data-model.md L368 {@code quotation_candidate} テーブルに対応。</p>
 *
 * <p>不変条件:</p>
 * <ul>
 *   <li>{@code estimatedDays > 0}</li>
 *   <li>{@code estimatedAmount} の金額は非負（{@link Money} 側で検証）</li>
 *   <li>{@code itinerarySummary} は経由港 UN/LOCODE を「{@code →}」区切りで結合（例: "JPTYO → SGSIN → DEHAM"）</li>
 *   <li>{@code voyageNumbers} は使用する航海番号のカンマ区切り表現（例: "V-0512,V-0531"）</li>
 * </ul>
 */
public record RouteCandidate(
        int estimatedDays,
        Money estimatedAmount,
        String itinerarySummary,
        String voyageNumbers) {

    public RouteCandidate {
        Objects.requireNonNull(estimatedAmount, "estimatedAmount");
        Objects.requireNonNull(itinerarySummary, "itinerarySummary");
        Objects.requireNonNull(voyageNumbers, "voyageNumbers");
        if (estimatedDays <= 0) {
            throw new IllegalArgumentException("estimatedDays は正の整数である必要があります: " + estimatedDays);
        }
        if (itinerarySummary.isBlank()) {
            throw new IllegalArgumentException("itinerarySummary は空にできません");
        }
    }
}
