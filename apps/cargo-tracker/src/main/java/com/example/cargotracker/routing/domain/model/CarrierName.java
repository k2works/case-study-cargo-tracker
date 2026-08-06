package com.example.cargotracker.routing.domain.model;

/**
 * 運送会社名。
 *
 * @param value 運送会社名（前後の空白は取り除く）
 */
public record CarrierName(String value) {

    private static final int MAX_LENGTH = 100;

    public CarrierName {
        if (value == null) {
            throw new IllegalArgumentException("運送会社は必須です");
        }
        value = value.strip();
        if (value.isEmpty()) {
            throw new IllegalArgumentException("運送会社が空です");
        }
        if (value.length() > MAX_LENGTH) {
            throw new IllegalArgumentException("運送会社は " + MAX_LENGTH + " 文字までです");
        }
    }
}
