package com.example.trackingms.domain.model.valueobjects;

import java.util.Objects;

/**
 * 追跡番号（値オブジェクト）
 * 形式: TRK-XXXXXX（X は数字 6 桁）
 */
public final class TrackingNumber {
    private static final String PREFIX = "TRK-";
    private final String number;

    public TrackingNumber(String number) {
        Objects.requireNonNull(number, "number must not be null");
        if (!number.startsWith(PREFIX) || number.length() != 10) {
            throw new IllegalArgumentException("Invalid tracking number format: " + number);
        }
        this.number = number;
    }

    public String getNumber() {
        return number;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        TrackingNumber that = (TrackingNumber) o;
        return Objects.equals(number, that.number);
    }

    @Override
    public int hashCode() {
        return Objects.hash(number);
    }

    @Override
    public String toString() {
        return number;
    }
}
