package com.example.cargotracker.trackingms.domain.model.valueobjects;

import java.util.Objects;
import java.util.regex.Pattern;

/**
 * UN/LOCODE（国連の港湾コード、5 文字）。
 *
 * <p>shared モジュール昇格候補。bookingms / routingms / handlingms の同名 VO と同一仕様。</p>
 */
public record UnLocode(String value) {

    private static final Pattern PATTERN = Pattern.compile("^[A-Z]{2}[A-Z0-9]{3}$");

    public UnLocode {
        Objects.requireNonNull(value, "value");
        if (!PATTERN.matcher(value).matches()) {
            throw new IllegalArgumentException(
                    "UnLocode は 5 文字の英数（最初 2 文字は国コード）である必要があります: " + value);
        }
    }
}
