package com.example.cargotracker.handlingms.domain.model.valueobjects;

import java.util.Objects;

/**
 * 追跡番号を表す値オブジェクト。
 *
 * <p>形式: {@code TRK-YYYYMMDD-XXXXXXXX}（IT4 bookingms 実装と整合）。
 * handlingms は bookingms から CargoSnapshot ACL 経由で受け取るため、
 * ここでは形式の最小限の検証のみ行う。</p>
 */
public record TrackingNumber(String value) {

    public TrackingNumber {
        Objects.requireNonNull(value, "value");
        if (value.isBlank()) {
            throw new IllegalArgumentException("TrackingNumber は空文字にできません");
        }
        if (!value.startsWith("TRK-")) {
            throw new IllegalArgumentException("TrackingNumber は 'TRK-' で始まる必要があります: " + value);
        }
        if (value.length() > 25) {
            throw new IllegalArgumentException("TrackingNumber は 25 文字以内である必要があります: " + value);
        }
    }
}
