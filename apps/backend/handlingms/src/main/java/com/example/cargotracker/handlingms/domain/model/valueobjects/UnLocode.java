package com.example.cargotracker.handlingms.domain.model.valueobjects;

import java.util.Objects;
import java.util.regex.Pattern;

/**
 * UN/LOCODE（国連の港湾コード、5 文字）。
 *
 * <p>形式: 2 文字国コード + 3 文字場所コード（例: JPYOK, USLAX）。</p>
 *
 * <p>bookingms と同形式。将来 shared モジュールへ昇格予定。</p>
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
