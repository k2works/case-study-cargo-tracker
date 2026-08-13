package com.example.cargotracker.tracking.domain.model.valueobjects;

/**
 * 追跡イベントに紐づく航海番号（Tracking Context 固有の型）。
 *
 * <p>Routing の {@code VoyageNumber} を参照しない（{@code domain-model.md}
 * 「VoyageNumber のコンテキスト分離設計」）。
 *
 * @param value 航海番号
 */
public record TrackingVoyageNumber(String value) {

    public TrackingVoyageNumber {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("航海番号は必須です");
        }
        value = value.strip();
    }
}
