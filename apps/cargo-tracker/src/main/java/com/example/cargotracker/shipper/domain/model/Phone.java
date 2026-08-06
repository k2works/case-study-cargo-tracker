package com.example.cargotracker.shipper.domain.model;

/**
 * 電話番号（任意）。
 *
 * @param value 電話番号。未設定は null
 */
public record Phone(String value) {

    private static final int MAX_LENGTH = 50;

    public Phone {
        if (value != null && value.length() > MAX_LENGTH) {
            throw new IllegalArgumentException("電話番号は " + MAX_LENGTH + " 文字以内です");
        }
    }

    public static Phone empty() {
        return new Phone(null);
    }
}
