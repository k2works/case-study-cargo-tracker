package com.example.cargotracker.shipper.domain.model.valueobjects;

/**
 * 荷主名（氏名または社名）。
 *
 * @param value 名称
 */
public record ShipperName(String value) {

    private static final int MAX_LENGTH = 200;

    public ShipperName {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("荷主名は必須です");
        }
        if (value.length() > MAX_LENGTH) {
            throw new IllegalArgumentException("荷主名は " + MAX_LENGTH + " 文字以内です");
        }
    }
}
