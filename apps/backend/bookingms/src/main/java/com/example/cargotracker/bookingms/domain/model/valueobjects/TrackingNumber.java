package com.example.cargotracker.bookingms.domain.model.valueobjects;

import java.util.Objects;
import java.util.regex.Pattern;

/**
 * 追跡番号。書式は {@code TRK-XXXXXXXXXX}（TRK- + 大文字英数 10 桁）。
 *
 * <p>data-model.md「追跡番号は TRK- + 大文字英数 10 桁（推測困難な書式）」に準拠。</p>
 */
public record TrackingNumber(String value) {

    private static final Pattern PATTERN = Pattern.compile("^TRK-[0-9A-Z]{10}$");

    public TrackingNumber {
        Objects.requireNonNull(value, "value");
        if (!PATTERN.matcher(value).matches()) {
            throw new IllegalArgumentException("TrackingNumber は 'TRK-' + 大文字英数 10 桁である必要があります: " + value);
        }
    }
}
