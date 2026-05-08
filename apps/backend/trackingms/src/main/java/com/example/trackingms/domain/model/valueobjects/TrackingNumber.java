package com.example.trackingms.domain.model.valueobjects;

import java.util.Objects;

/**
 * 追跡番号（値オブジェクト）
 * 形式: TRK-XXXXXX（X は数字 6 桁）
 */
public record TrackingNumber(String number) {
    private static final String PREFIX = "TRK-";

    public TrackingNumber {
        Objects.requireNonNull(number, "number must not be null");
        if (!number.startsWith(PREFIX) || number.length() != 10) {
            throw new IllegalArgumentException("Invalid tracking number format: " + number);
        }
    }

    @Override
    public String toString() {
        return number;
    }
}
