package com.example.cargotracker.archfixture.crossbc.beta.domain.model;

/** 別 BC の値オブジェクト（実コードの {@code VoyageNumber} と同じ形）。 */
public record BetaVoyageNumber(String value) {

    public BetaVoyageNumber {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("航海番号は必須です");
        }
    }
}
