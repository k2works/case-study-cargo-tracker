package com.example.trackingms.domain.model.valueobjects;

import java.util.Objects;

/**
 * 追跡番号（値オブジェクト）
 * 形式: TRK-XXXXXX（X は数字 6 桁）
 */
public record TrackingNumber(String number) {
    private static final java.util.regex.Pattern PATTERN =
            java.util.regex.Pattern.compile("^TRK-\\d{6}$");

    public TrackingNumber {
        Objects.requireNonNull(number, "number must not be null");
        if (!PATTERN.matcher(number).matches()) {
            throw new IllegalArgumentException("Invalid tracking number format: " + number);
        }
    }

    @Override
    public String toString() {
        return number;
    }
}
