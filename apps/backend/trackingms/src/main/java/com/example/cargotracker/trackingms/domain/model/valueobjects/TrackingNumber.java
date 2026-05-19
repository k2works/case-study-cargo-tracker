package com.example.cargotracker.trackingms.domain.model.valueobjects;

import java.util.Objects;
import java.util.regex.Pattern;

/**
 * 追跡番号を表す値オブジェクト。
 *
 * <p>形式: {@code TRK-YYYYMMDD-XXXXXXXX}（例: {@code TRK-20260518-3053F0B8}）。
 * bookingms の生成ロジック {@code "TRK-" + YYYYMMDD + "-" + UUID前8桁大文字} と整合。</p>
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
