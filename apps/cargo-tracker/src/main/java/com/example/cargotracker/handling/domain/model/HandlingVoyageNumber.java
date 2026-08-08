package com.example.cargotracker.handling.domain.model;

/**
 * 荷役作業に紐づく航海番号（Handling モジュール固有の型）。
 *
 * <p>Routing の {@code VoyageNumber} を参照しない（{@code domain-model.md}
 * 「VoyageNumber のコンテキスト分離設計」）。
 *
 * @param value 航海番号
 */
public record HandlingVoyageNumber(String value) {

    public HandlingVoyageNumber {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("航海番号は必須です");
        }
        value = value.strip();
    }
}
