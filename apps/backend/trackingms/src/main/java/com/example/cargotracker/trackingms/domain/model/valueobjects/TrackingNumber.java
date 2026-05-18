package com.example.cargotracker.trackingms.domain.model.valueobjects;

import java.util.Objects;
import java.util.regex.Pattern;

/**
 * 追跡番号。書式は {@code TRK-XXXXXXXXXX}（TRK- + 大文字英数 10 桁）。
 *
 * <p>bookingms の {@code TrackingNumber} と同一書式。{@code CargoTrackedEvent} 経由で
 * 受領して trackingms 内でも値オブジェクトとして扱う。</p>
 */
public record TrackingNumber(String value) {

    private static final Pattern PATTERN = Pattern.compile("^TRK-[0-9A-Z]{10}$");

    public TrackingNumber {
        Objects.requireNonNull(value, "value");
        if (!PATTERN.matcher(value).matches()) {
            throw new IllegalArgumentException(
                    "TrackingNumber は 'TRK-' + 大文字英数 10 桁である必要があります: " + value);
        }
    }
}
