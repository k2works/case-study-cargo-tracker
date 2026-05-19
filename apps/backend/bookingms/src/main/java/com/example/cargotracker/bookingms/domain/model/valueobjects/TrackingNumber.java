package com.example.cargotracker.bookingms.domain.model.valueobjects;

import java.util.Objects;
import java.util.regex.Pattern;

/**
 * 追跡番号。書式は {@code TRK-YYYYMMDD-XXXXXXXX}（TRK- + 日付 8 桁 + ハイフン + 大文字英数 8 桁）。
 *
 * <p>bookingms の生成ロジック {@code "TRK-" + YYYYMMDD + "-" + UUID前8桁大文字} と整合。</p>
 */
public record TrackingNumber(String value) {

    private static final Pattern PATTERN = Pattern.compile("^TRK-\\d{8}-[0-9A-Z]{8}$");

    public TrackingNumber {
        Objects.requireNonNull(value, "value");
        if (!PATTERN.matcher(value).matches()) {
            throw new IllegalArgumentException(
                    "TrackingNumber は 'TRK-YYYYMMDD-XXXXXXXX' 形式である必要があります: " + value);
        }
    }
}
