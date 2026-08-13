package com.example.cargotracker.routing.domain.model.valueobjects;

/**
 * 船名。
 *
 * @param value 船名（前後の空白は取り除く）
 */
public record VesselName(String value) {

    private static final int MAX_LENGTH = 100;

    public VesselName {
        if (value == null) {
            throw new IllegalArgumentException("船名は必須です");
        }
        value = value.strip();
        if (value.isEmpty()) {
            throw new IllegalArgumentException("船名が空です");
        }
        if (value.length() > MAX_LENGTH) {
            throw new IllegalArgumentException("船名は " + MAX_LENGTH + " 文字までです");
        }
    }
}
